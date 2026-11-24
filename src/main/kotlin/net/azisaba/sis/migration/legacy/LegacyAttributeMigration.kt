package net.azisaba.sis.migration.legacy

import java.util.Locale
import java.util.UUID

/**
 * Restores attribute modifiers saved by 1.15.2 builds.
 *
 * 1.15.2 wrote `attribute-modifiers` keyed by the NMS name (`generic.attackDamage`) with modifiers identified by
 * `uuid`, and `slot: HAND`. Newer servers expect registry keys (`minecraft:attack_damage`) and the `HAND` slot now
 * means "both hands", so the whole map was discarded while the ItemStack was deserialized. Like
 * [LegacySkullMigration], this has to rewrite the raw YAML text before Bukkit builds the ItemStacks, see [fixRawYaml].
 */
object LegacyAttributeMigration {
    const val FROM_VERSION = 3
    const val TO_VERSION = 4

    private const val ATTRIBUTE_MODIFIERS = "attribute-modifiers"

    /** The converted YAML text, or the input itself when nothing had to change. */
    fun fixRawYaml(text: String): String = LegacyYaml.rewrite(text) { root ->
        LegacyYaml.anyMap(root) { map ->
            @Suppress("UNCHECKED_CAST")
            val modifiers = map[ATTRIBUTE_MODIFIERS] as? MutableMap<Any?, Any?>
            modifiers != null && fixModifiers(map, modifiers)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fixModifiers(meta: MutableMap<Any?, Any?>, modifiers: Map<Any?, Any?>): Boolean {
        val converted = LinkedHashMap<Any?, Any?>()
        var changed = false
        for ((oldKey, values) in modifiers) {
            val key = convertAttributeKey(oldKey.toString())
            if (key != oldKey.toString()) changed = true
            val list = converted.getOrPut(key) { ArrayList<Any?>() } as MutableList<Any?>
            for (value in values as? List<*> ?: continue) {
                if (value is MutableMap<*, *>) {
                    val mod = value as MutableMap<Any?, Any?>
                    if (convertModifier(mod)) changed = true
                    if (makeKeyUnique(mod, list)) changed = true
                }
                list += value
            }
        }
        if (changed) meta[ATTRIBUTE_MODIFIERS] = converted
        return changed
    }

    /**
     * 1.15.2 let several modifiers of one attribute share a uuid (e.g. one per slot or operation). The current game
     * requires the key to be unique per attribute and refuses the duplicate, so later ones get a numeric suffix.
     */
    private fun makeKeyUnique(modifier: MutableMap<Any?, Any?>, existing: List<Any?>): Boolean {
        val base = modifier["key"]?.toString() ?: return false
        fun taken(key: String) = existing.any { (it as? Map<*, *>)?.get("key")?.toString() == key }
        var candidate = base
        var n = 1
        while (taken(candidate)) candidate = "${base}_${++n}"
        if (candidate == base) return false
        modifier["key"] = candidate
        return true
    }

    private fun convertModifier(modifier: MutableMap<Any?, Any?>): Boolean {
        var changed = false
        if ("uuid" in modifier) {
            // The current API identifies a modifier by a NamespacedKey; the uuid string is a valid key value.
            var id = modifier.remove("uuid").toString().lowercase(Locale.ROOT)
            modifier.remove("name")
            runCatching { id = UUID.fromString(id).toString() }
            modifier.putIfAbsent("key", "minecraft:$id")
            changed = true
        }
        val slot = modifier["slot"] as? String
        if (slot != null) {
            // 1.15.2 HAND/OFF_HAND were single slots; the new "hand" group means both hands.
            val converted = when (slot.uppercase(Locale.ROOT)) {
                "HAND" -> "mainhand"
                "OFF_HAND" -> "offhand"
                else -> slot
            }
            if (converted != slot) {
                modifier["slot"] = converted
                changed = true
            }
        }
        return changed
    }

    private val PREFIXES = listOf("GENERIC_", "HORSE_", "ZOMBIE_", "PLAYER_")
    private val CAMEL = Regex("([a-z0-9])([A-Z])")

    /**
     * `generic.attackDamage`, `GENERIC_ATTACK_DAMAGE` and `minecraft:generic.attack_damage` all become
     * `minecraft:attack_damage`.
     */
    fun convertAttributeKey(raw: String): String {
        var name = raw.trim().substringAfter(':')
        if ('.' in name) {
            name = name.substringAfter('.')
        } else if (name == name.uppercase(Locale.ROOT)) {
            // Enum style: GENERIC_ATTACK_DAMAGE, HORSE_JUMP_STRENGTH, ZOMBIE_SPAWN_REINFORCEMENTS
            PREFIXES.firstOrNull { name.startsWith(it) }?.let { name = name.removePrefix(it) }
        }
        return "minecraft:" + name.replace(CAMEL, "$1_$2").lowercase(Locale.ROOT)
    }
}
