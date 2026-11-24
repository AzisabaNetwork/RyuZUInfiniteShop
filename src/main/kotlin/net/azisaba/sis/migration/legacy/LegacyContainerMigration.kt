package net.azisaba.sis.migration.legacy

import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.io.File
import java.util.Locale
import java.util.logging.Level

/**
 * Keeps the contents of containers (shulker boxes...) saved by 1.15.2 builds.
 *
 * 1.15.2 wrote such items with `meta-type: TILE_ENTITY`; their contents only lived in `BlockEntityTag.Items` of the
 * base64 "internal" NBT, in the 1.15.2 item format (`Count` byte, `tag`). Current servers read that tag without
 * running the data fixers, so the contents came back broken: in the real shop `world,1830,92,739` the prize, a
 * shulker box holding 27 items, turned into a single gold ingot. Here the whole legacy item is rebuilt through
 * Minecraft's DataFixer ([ItemStack.deserializeBytes]), which upgrades the box and every item inside it, and replaces
 * the broken item.
 *
 * The legacy item is taken from the file itself when it has not been loaded by a current server yet, otherwise from
 * the shop's 1.15.2 backup. An item is only replaced when it still looks broken (same container with fewer items,
 * or the first item of the box in place of the box), so items edited since are left alone.
 */
object LegacyContainerMigration {
    const val FROM_VERSION = 8
    const val TO_VERSION = 9

    /** Present in the YAML of every 1.15.2 block entity item. */
    private const val MARKER = "blockMaterial:"
    private const val BLOCK_ENTITY_TAG = "BlockEntityTag"
    private const val LEGACY_DATA_VERSION = 2230 // 1.15.2

    /**
     * [rawText] is the file's text before Bukkit loaded it; it is used when it still holds the 1.15.2 items.
     * Returns how many containers were restored.
     */
    fun restore(yaml: YamlConfiguration, rawText: String?, shopsDir: File, fileName: String): Int {
        val legacy = (rawText?.takeIf { MARKER in it }?.let(LegacyYaml::parse)
            ?: LegacyYaml.findBackup(shopsDir, fileName, MARKER)) as? Map<*, *> ?: return 0
        var count = 0
        for (key in yaml.getKeys(false)) {
            val value = yaml.get(key)
            val restored = restore(value, legacy[key]) { count++ }
            if (restored !== value) yaml.set(key, restored)
        }
        return count
    }

    @Suppress("UNCHECKED_CAST")
    private fun restore(current: Any?, legacy: Any?, onRestored: () -> Unit): Any? {
        if (legacy == null || current == null) return current
        when {
            current is ItemStack -> {
                val rebuilt = (legacy as? Map<*, *>)?.let { rebuildIfBroken(current, it) } ?: return current
                onRestored()
                return rebuilt
            }
            current is ConfigurationSection && legacy is Map<*, *> -> for (key in current.getKeys(false)) {
                val value = current.get(key)
                val restored = restore(value, legacy[key], onRestored)
                if (restored !== value) current.set(key, restored)
            }
            current is MutableMap<*, *> && legacy is Map<*, *> -> (current as MutableMap<Any?, Any?>).entries.forEach { entry ->
                val restored = restore(entry.value, legacy[entry.key], onRestored)
                if (restored !== entry.value) entry.setValue(restored)
            }
            current is MutableList<*> && legacy is List<*> -> {
                val list = current as MutableList<Any?>
                for (i in 0 until minOf(list.size, legacy.size)) {
                    val restored = restore(list[i], legacy[i], onRestored)
                    if (restored !== list[i]) list[i] = restored
                }
            }
        }
        return current
    }

    private fun rebuildIfBroken(current: ItemStack, legacyItem: Map<*, *>): ItemStack? {
        val legacyNbt = legacyItemNbt(legacyItem) ?: return null
        val legacyContents = legacyContents(legacyNbt)
        if (legacyContents.isNullOrEmpty() || !looksBroken(current, legacyItem, legacyContents)) return null
        return try {
            ItemStack.deserializeBytes(LegacyNbt.write(legacyNbt)).takeUnless { it.type.isAir }
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "[SearchableInfiniteShop] Could not rebuild a 1.15.2 ${legacyItem["type"]}", e)
            null
        }
    }

    /** The broken item is the same container with fewer items, or the first item of the box in place of the box. */
    private fun looksBroken(current: ItemStack, legacyItem: Map<*, *>, legacyContents: List<*>): Boolean {
        val legacyType = Material.matchMaterial(legacyItem["type"].toString())
        if (legacyType != null && current.type == legacyType) {
            val meta = current.itemMeta as? BlockStateMeta
            val container = meta?.takeIf { it.hasBlockState() }?.blockState as? Container ?: return true
            return container.inventory.contents.count { it != null && !it.type.isAir } < legacyContents.size
        }
        val firstId = (legacyContents[0] as? Map<*, *>)?.get("id") as? String ?: return false
        return Material.matchMaterial(firstId)?.let { current.type == it } ?: false
    }

    private fun legacyContents(legacyNbt: Map<String, Any?>): List<*>? =
        ((legacyNbt["tag"] as? Map<*, *>)?.get(BLOCK_ENTITY_TAG) as? Map<*, *>)?.get("Items") as? List<*>

    /**
     * The 1.15.2 item NBT (`id`, `Count`, `tag`, `DataVersion`) of a raw legacy YAML item, as the DataFixer expects
     * it: the "internal" NBT plus the name and lore that Bukkit kept outside of it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun legacyItemNbt(legacyItem: Map<*, *>): Map<String, Any?>? {
        if (legacyItem["=="] != LegacyYaml.ITEM_STACK_CLASS) return null
        val type = legacyItem["type"] as? String ?: return null
        val meta = legacyItem["meta"] as? Map<*, *> ?: return null
        val tag = LegacyYaml.internalNbt(legacyItem) ?: return null
        if (tag[BLOCK_ENTITY_TAG] !is Map<*, *>) return null

        val display = tag["display"] as? MutableMap<String, Any?> ?: LinkedHashMap()
        (meta["display-name"] as? String)?.let { if ("Name" !in display) display["Name"] = jsonText(it) }
        (meta["lore"] as? List<*>)?.let { lore ->
            if ("Lore" !in display) display["Lore"] = LegacyNbt.NbtList(8).apply { lore.forEach { add(jsonText(it.toString())) } }
        }
        if (display.isNotEmpty()) tag["display"] = display
        renameLegacyAttributes(tag, 0)

        val amount = (legacyItem["amount"] as? Number)?.toInt() ?: 1
        val version = (legacyItem["v"] as? Number)?.toInt()?.takeIf { it > 0 } ?: LEGACY_DATA_VERSION
        return linkedMapOf(
            "id" to "minecraft:" + type.lowercase(Locale.ROOT),
            "Count" to amount.coerceIn(1, 127).toByte(),
            "tag" to tag,
            "DataVersion" to version,
        )
    }

    /**
     * The items in the box may carry 1.15.2 camelCase attribute names (`generic.attackDamage`, e.g. MythicMobs 4
     * items), which Paper 1.21.11's DataFixer rejects together with the whole box. They are rewritten to the 1.16
     * form the later fixes understand.
     */
    @Suppress("UNCHECKED_CAST")
    private fun renameLegacyAttributes(node: Any?, depth: Int) {
        if (depth > 64) return
        when (node) {
            is Map<*, *> -> {
                (node["AttributeModifiers"] as? List<*>)?.forEach { modifier ->
                    val m = modifier as? MutableMap<Any?, Any?> ?: return@forEach
                    (m["AttributeName"] as? String)?.let { m["AttributeName"] = legacyAttributeKey(it) }
                }
                node.values.forEach { renameLegacyAttributes(it, depth + 1) }
            }
            is List<*> -> node.forEach { renameLegacyAttributes(it, depth + 1) }
        }
    }

    private val CAMEL = Regex("([a-z0-9])([A-Z])")

    internal fun legacyAttributeKey(raw: String): String =
        "minecraft:" + raw.trim().substringAfter(':').replace(CAMEL, "$1_$2").lowercase(Locale.ROOT)

    /** 1.15.2 kept display names and lore as JSON text; Bukkit's YAML has the legacy "§" form. */
    private fun jsonText(legacyText: String): String = JsonObject().apply { addProperty("text", legacyText) }.toString()
}
