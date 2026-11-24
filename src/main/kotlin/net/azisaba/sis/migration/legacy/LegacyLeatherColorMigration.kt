package net.azisaba.sis.migration.legacy

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import java.io.File

/**
 * Keeps the dye of leather armor saved by 1.15.2 builds.
 *
 * 1.15.2 wrote leather armor with `meta-type: LEATHER_ARMOR`. Current servers give leather helmets, chestplates,
 * leggings and boots a `COLORABLE_ARMOR` meta instead, and converting the deserialized LEATHER_ARMOR meta into it
 * only copies the color from another COLORABLE_ARMOR meta, so the dye was silently dropped. The meta type is
 * rewritten in the raw YAML text before Bukkit builds the ItemStacks ([fixRawYaml]). Files migrated before this step
 * already lost the color; it is put back from the shop's 1.15.2 backup by [restore].
 */
object LegacyLeatherColorMigration {
    const val FROM_VERSION = 6
    const val TO_VERSION = 7

    private val COLORABLE_ARMOR_TYPES = setOf("LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS")

    /** The converted YAML text, or the input itself when nothing had to change. */
    fun fixRawYaml(text: String): String = LegacyYaml.rewrite(text) { root ->
        LegacyYaml.anyMap(root) { map ->
            @Suppress("UNCHECKED_CAST")
            val meta = map["meta"] as? MutableMap<Any?, Any?>
            if (map["=="] == LegacyYaml.ITEM_STACK_CLASS && map["type"].toString() in COLORABLE_ARMOR_TYPES && meta?.get("meta-type") == "LEATHER_ARMOR") {
                meta["meta-type"] = "COLORABLE_ARMOR"
                true
            } else false
        }
    }

    /**
     * Puts back the dye of leather armor that lost it in an earlier migration, taken from the item at the same
     * position in the shop's 1.15.2 backup. Only undyed items of the same material are touched, so armor dyed or
     * replaced since is left alone. Returns how many items got their color back.
     */
    fun restore(yaml: YamlConfiguration, shopsDir: File, fileName: String): Int =
        LegacyYaml.restoreItems(yaml, LegacyYaml.findBackup(shopsDir, fileName, "meta-type: LEATHER_ARMOR"), ::restoreColor)

    private fun restoreColor(item: ItemStack, legacyItem: Map<*, *>): Boolean {
        if (item.type.name != legacyItem["type"].toString()) return false
        val meta = item.itemMeta as? LeatherArmorMeta ?: return false
        if (meta.color != Bukkit.getItemFactory().defaultLeatherColor) return false
        val color = legacyColor(legacyItem) ?: return false
        if (color == meta.color) return false
        meta.setColor(color)
        item.itemMeta = meta
        return true
    }

    private fun legacyColor(legacyItem: Map<*, *>): Color? {
        val color = (legacyItem["meta"] as? Map<*, *>)?.get("color") as? Map<*, *> ?: return null
        val r = color["RED"] as? Number ?: return null
        val g = color["GREEN"] as? Number ?: return null
        val b = color["BLUE"] as? Number ?: return null
        return runCatching { Color.fromRGB(r.toInt(), g.toInt(), b.toInt()) }.getOrNull()
    }
}
