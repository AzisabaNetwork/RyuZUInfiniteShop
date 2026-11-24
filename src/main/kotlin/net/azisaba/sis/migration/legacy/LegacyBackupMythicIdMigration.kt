package net.azisaba.sis.migration.legacy

import net.azisaba.sis.item.mythic.LegacyMythicItemMatcher
import net.azisaba.sis.migration.LegacyMythicItem
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File

/**
 * Restores the MythicMobs ID of shop items that lost it on the way from 1.15.2, using the 1.15.2 copy of the same
 * shop file kept in `shops/backup/<timestamp>/`.
 *
 * Items cannot always be told apart by their look (e.g. a player dice and the rigged dice share name, lore and skin),
 * so the ID is taken from the item at the same position in the backup, whose base64 "internal" NBT still holds
 * `MYTHIC_TYPE`. It is only applied when that backup item has the same name and lore, so trades edited since are
 * left alone.
 */
object LegacyBackupMythicIdMigration {
    const val FROM_VERSION = 5
    const val TO_VERSION = 6

    /** Returns how many items got their ID back. */
    fun restore(yaml: YamlConfiguration, shopsDir: File, fileName: String): Int {
        val legacy = LegacyYaml.findBackup(shopsDir, fileName, "internal:") as? Map<*, *> ?: return 0
        var count = 0
        for (key in yaml.getKeys(false)) {
            val current = yaml.get(key)
            val restored = restore(current, legacy[key]) { count++ }
            if (restored !== current) yaml.set(key, restored)
        }
        return count
    }

    /** [current] with its items replaced by MythicMobs references where the backup knows their ID. */
    private fun restore(current: Any?, legacy: Any?, onRestored: () -> Unit): Any? {
        if (legacy == null) return current
        return when {
            current is ItemStack -> {
                val legacyItem = legacy as? Map<*, *> ?: return current
                val id = legacyMythicType(legacyItem)
                if (id == null || !sameLook(current, legacyItem)) current else {
                    onRestored()
                    LegacyMythicItem(id, current.amount)
                }
            }
            current is ConfigurationSection && legacy is Map<*, *> -> {
                for (key in current.getKeys(false)) {
                    val value = current.get(key)
                    val restored = restore(value, legacy[key], onRestored)
                    if (restored !== value) current.set(key, restored)
                }
                current
            }
            current is Map<*, *> && legacy is Map<*, *> -> {
                var changed = false
                val result = LinkedHashMap<Any?, Any?>()
                for ((k, v) in current) {
                    val restored = restore(v, legacy[k], onRestored)
                    if (restored !== v) changed = true
                    result[k] = restored
                }
                if (changed) result else current
            }
            current is List<*> && legacy is List<*> -> {
                var changed = false
                val result = current.mapIndexed { i, v ->
                    val restored = if (i < legacy.size) restore(v, legacy[i], onRestored) else v
                    if (restored !== v) changed = true
                    restored
                }
                if (changed) result else current
            }
            else -> current
        }
    }

    private fun legacyMythicType(legacyItem: Map<*, *>): String? =
        (LegacyYaml.internalNbt(legacyItem)?.get("MYTHIC_TYPE") as? String)?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun sameLook(item: ItemStack, legacyItem: Map<*, *>): Boolean {
        val legacyMeta = legacyItem["meta"] as? Map<*, *> ?: return false
        val meta = item.itemMeta
        val name = if (meta != null && meta.hasDisplayName()) LegacyMythicItemMatcher.readableText(meta.displayName) else null
        if (plain(name) != plain(legacyMeta["display-name"])) return false
        val lore = if (meta != null && meta.hasLore()) meta.lore.orEmpty().map { plain(LegacyMythicItemMatcher.readableText(it)) } else emptyList()
        val legacyLore = (legacyMeta["lore"] as? List<*>).orEmpty().map(::plain)
        if (lore != legacyLore) return false
        // Without a name or lore, the material is the only thing left to compare.
        return name != null || lore.isNotEmpty() || item.type.name == legacyItem["type"].toString()
    }

    @Suppress("DEPRECATION")
    private fun plain(text: Any?): String? = text?.let { ChatColor.stripColor(it.toString())?.trim()?.takeIf(String::isNotEmpty) }
}
