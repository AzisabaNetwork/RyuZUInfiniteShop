package net.azisaba.sis.migration.legacy

import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.banner.Pattern
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ShieldMeta
import java.io.File

/**
 * Keeps the base color and banner patterns of shields saved by 1.15.2 builds.
 *
 * 1.15.2 wrote shields with `meta-type: TILE_ENTITY`; their colors only lived in the `BlockEntityTag` of the base64
 * "internal" NBT, as numeric `Base` / `Patterns[].Color` dye ids. Current servers read that tag as-is without running
 * the data fixers, so every dyed shield came back plain white. The item meta is rewritten in the raw YAML text into
 * the current `SHIELD` meta before Bukkit builds the ItemStacks ([fixRawYaml]). Files migrated before this step
 * already lost the colors; they are put back from the shop's 1.15.2 backup by [restore].
 */
object LegacyShieldColorMigration {
    const val FROM_VERSION = 7
    const val TO_VERSION = 8

    private const val BLOCK_ENTITY_TAG = "BlockEntityTag"

    /** Dye ids as used by 1.13 - 1.20.4 banner NBT (= wool data). */
    private val DYE_BY_ID = listOf(
        DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
        DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
        DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
        DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK,
    )

    /** Legacy pattern codes, mapped to their current keys. */
    private val PATTERN_KEYS = mapOf(
        "b" to "base", "bl" to "square_bottom_left", "br" to "square_bottom_right",
        "tl" to "square_top_left", "tr" to "square_top_right", "bs" to "stripe_bottom",
        "ts" to "stripe_top", "ls" to "stripe_left", "rs" to "stripe_right",
        "cs" to "stripe_center", "ms" to "stripe_middle", "drs" to "stripe_downright",
        "dls" to "stripe_downleft", "ss" to "small_stripes", "cr" to "cross",
        "sc" to "straight_cross", "bt" to "triangle_bottom", "tt" to "triangle_top",
        "bts" to "triangles_bottom", "tts" to "triangles_top", "ld" to "diagonal_left",
        "rd" to "diagonal_up_right", "lud" to "diagonal_up_left", "rud" to "diagonal_right",
        "mc" to "circle", "mr" to "rhombus", "vh" to "half_vertical",
        "hh" to "half_horizontal", "vhr" to "half_vertical_right",
        "hhb" to "half_horizontal_bottom", "bo" to "border", "cbo" to "curly_border",
        "cre" to "creeper", "gra" to "gradient", "gru" to "gradient_up",
        "bri" to "bricks", "sku" to "skull", "flo" to "flower", "moj" to "mojang",
        "glb" to "globe", "pig" to "piglin", "flw" to "flow", "gus" to "guster",
    )

    private data class LegacyPattern(val color: DyeColor, val key: String)

    private data class LegacyShield(val base: DyeColor, val patterns: List<LegacyPattern>) {
        companion object {
            /** 1.15.2 drew any shield with a BlockEntityTag as a banner, defaulting a missing `Base` to white. */
            fun of(blockEntity: Map<*, *>): LegacyShield {
                val patterns = (blockEntity["Patterns"] as? List<*>).orEmpty().mapNotNull { entry ->
                    val p = entry as? Map<*, *> ?: return@mapNotNull null
                    val color = dye(p["Color"]) ?: return@mapNotNull null
                    val key = PATTERN_KEYS[p["Pattern"].toString()] ?: return@mapNotNull null
                    LegacyPattern(color, key)
                }
                return LegacyShield(dye(blockEntity["Base"]) ?: DyeColor.WHITE, patterns)
            }
        }
    }

    private fun dye(id: Any?): DyeColor? = (id as? Number)?.toInt()?.let(DYE_BY_ID::getOrNull)

    /** The converted YAML text, or the input itself when nothing had to change. */
    fun fixRawYaml(text: String): String = LegacyYaml.rewrite(text) { root ->
        LegacyYaml.anyMap(root) { map ->
            @Suppress("UNCHECKED_CAST")
            val meta = map["meta"] as? MutableMap<Any?, Any?>
            map["=="] == LegacyYaml.ITEM_STACK_CLASS && map["type"].toString() == "SHIELD" &&
                meta?.get("meta-type") == "TILE_ENTITY" && fixShieldMeta(meta)
        }
    }

    private fun fixShieldMeta(meta: MutableMap<Any?, Any?>): Boolean {
        val internal = meta["internal"] as? String ?: return false
        val nbt = LegacyNbt.readInternal(internal) ?: return false
        val shield = LegacyShield.of(nbt[BLOCK_ENTITY_TAG] as? Map<*, *> ?: return false)

        nbt.remove(BLOCK_ENTITY_TAG)
        if (nbt.isEmpty()) meta.remove("internal")
        else meta["internal"] = runCatching { LegacyNbt.writeInternal(nbt) }.getOrElse { return false }
        meta.remove("blockMaterial")
        meta["meta-type"] = "SHIELD"
        meta["base-color"] = shield.base.name
        if (shield.patterns.isNotEmpty()) {
            meta["patterns"] = shield.patterns.map { linkedMapOf("==" to "Pattern", "color" to it.color.name, "pattern" to "minecraft:${it.key}") }
        }
        return true
    }

    /**
     * Puts back the colors of shields that lost them in an earlier migration, taken from the item at the same position
     * in the shop's 1.15.2 backup. Only shields still plain (white or no base color, no patterns) are touched, so
     * shields recolored or replaced since are left alone. Returns how many items got their colors back.
     */
    fun restore(yaml: YamlConfiguration, shopsDir: File, fileName: String): Int =
        LegacyYaml.restoreItems(yaml, LegacyYaml.findBackup(shopsDir, fileName, "blockMaterial: SHIELD"), ::restoreColors)

    private fun restoreColors(item: ItemStack, legacyItem: Map<*, *>): Boolean {
        if (item.type != Material.SHIELD || legacyItem["type"].toString() != "SHIELD") return false
        val meta = item.itemMeta as? ShieldMeta ?: return false
        if (meta.numberOfPatterns() > 0 || (meta.baseColor != null && meta.baseColor != DyeColor.WHITE)) return false
        val blockEntity = LegacyYaml.internalNbt(legacyItem)?.get(BLOCK_ENTITY_TAG) as? Map<*, *> ?: return false
        val shield = LegacyShield.of(blockEntity)
        if (shield.base == meta.baseColor && shield.patterns.isEmpty()) return false

        meta.baseColor = shield.base
        meta.patterns = shield.patterns.mapNotNull { p ->
            Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(p.key))?.let { Pattern(p.color, it) }
        }
        item.itemMeta = meta
        return true
    }
}
