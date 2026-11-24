package net.azisaba.sis.migration.legacy

/**
 * Restores MythicMobs items (and with them their attributes) saved by 1.15.2 builds.
 *
 * Those items were saved as plain ItemStacks whose only link to MythicMobs is the `MYTHIC_TYPE` tag inside the
 * base64 "internal" NBT. Everything else (attributes, custom model data...) lived in MythicMobs' own item config.
 * Newer servers ignore "internal", so the ID was lost and no attributes could be regenerated.
 *
 * Instead of rebuilding the ItemStack, each such item is rewritten as a 1.x `MythicItem` (id + amount), which the
 * importer turns into a MythicMobs entry. This works on the raw YAML text because the tag is dropped as soon as
 * Bukkit deserializes the ItemStack.
 */
object LegacyMythicTypeMigration {
    const val FROM_VERSION = 4
    const val TO_VERSION = 5

    const val MYTHIC_ITEM_CLASS = "ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem"
    private const val MYTHIC_TYPE = "MYTHIC_TYPE"

    /** The converted YAML text, or the input itself when no legacy Mythic item was found. */
    fun fixRawYaml(text: String): String {
        val root = LegacyYaml.parse(text) ?: return text
        var changed = false
        val fixed = replace(root) { changed = true }
        return if (changed) LegacyYaml.dump(fixed) else text
    }

    @Suppress("UNCHECKED_CAST")
    private fun replace(node: Any?, onChange: () -> Unit): Any? {
        when (node) {
            is Map<*, *> -> {
                toMythicItem(node)?.let {
                    onChange()
                    return it
                }
                (node as MutableMap<Any?, Any?>).entries.forEach { it.setValue(replace(it.value, onChange)) }
            }
            is List<*> -> (node as MutableList<Any?>).replaceAll { replace(it, onChange) }
        }
        return node
    }

    private fun toMythicItem(item: Map<*, *>): Map<String, Any?>? {
        if (item["=="] != LegacyYaml.ITEM_STACK_CLASS) return null
        val id = (LegacyYaml.internalNbt(item) ?: return null)[MYTHIC_TYPE] as? String
        if (id.isNullOrBlank()) return null
        return linkedMapOf("==" to MYTHIC_ITEM_CLASS, "id" to id, "amount" to ((item["amount"] as? Number)?.toInt() ?: 1))
    }
}
