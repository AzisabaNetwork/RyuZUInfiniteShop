package net.azisaba.sis.migration.legacy

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/**
 * Raw YAML helpers for the 1.x migration steps. Bukkit drops the 1.15.2 data while it deserializes ItemStacks, so
 * the steps read the file as plain maps and lists (without Bukkit) and write the fixed text back.
 */
internal object LegacyYaml {
    const val ITEM_STACK_CLASS = "org.bukkit.inventory.ItemStack"

    /** The text as plain maps and lists, or null when it is not valid YAML. */
    fun parse(text: String): Any? = runCatching { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text) }.getOrNull()

    fun dump(root: Any?): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            splitLines = false
            width = Int.MAX_VALUE
        }
        return Yaml(options).dump(root)
    }

    /** Runs [fix] on the parsed text; returns the fixed text, or [text] itself when nothing changed. */
    fun rewrite(text: String, fix: (Any?) -> Boolean): String {
        val root = parse(text) ?: return text
        return if (fix(root)) dump(root) else text
    }

    /** Calls [visit] on every map in [node] (depth first); true when any call returned true. */
    @Suppress("UNCHECKED_CAST")
    fun anyMap(node: Any?, visit: (MutableMap<Any?, Any?>) -> Boolean): Boolean {
        var changed = false
        when (node) {
            is Map<*, *> -> {
                val map = node as MutableMap<Any?, Any?>
                if (visit(map)) changed = true
                for (value in map.values.toList()) if (anyMap(value, visit)) changed = true
            }
            is List<*> -> for (value in node) if (anyMap(value, visit)) changed = true
        }
        return changed
    }

    /**
     * Walks the loaded file and its raw 1.15.2 form side by side and counts the items [restore] fixed.
     * [restore] gets an item of the loaded file and the raw map at the same position.
     */
    fun restoreItems(yaml: YamlConfiguration, legacy: Any?, restore: (org.bukkit.inventory.ItemStack, Map<*, *>) -> Boolean): Int {
        val root = legacy as? Map<*, *> ?: return 0
        return yaml.getKeys(false).sumOf { walk(yaml.get(it), root[it], restore) }
    }

    private fun walk(current: Any?, legacy: Any?, restore: (org.bukkit.inventory.ItemStack, Map<*, *>) -> Boolean): Int {
        if (current == null || legacy == null) return 0
        return when {
            current is org.bukkit.inventory.ItemStack -> if (legacy is Map<*, *> && restore(current, legacy)) 1 else 0
            current is ConfigurationSection && legacy is Map<*, *> -> current.getKeys(false).sumOf { walk(current.get(it), legacy[it], restore) }
            current is Map<*, *> && legacy is Map<*, *> -> current.entries.sumOf { (k, v) -> walk(v, legacy[k], restore) }
            current is List<*> && legacy is List<*> -> (0 until minOf(current.size, legacy.size)).sumOf { walk(current[it], legacy[it], restore) }
            else -> 0
        }
    }

    /**
     * The oldest backup of [fileName] in `shops/backup/<timestamp>/` whose text contains [marker] (still in the
     * 1.15.2 format), parsed without Bukkit.
     */
    fun findBackup(shopsDir: File, fileName: String, marker: String): Any? {
        val dirs = File(shopsDir, "backup").listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: return null
        for (dir in dirs) {
            val backup = File(dir, fileName)
            if (!backup.isFile) continue
            val text = runCatching { backup.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            if (marker !in text) continue
            parse(text)?.let { return it }
        }
        return null
    }

    /** The NBT in the "internal" string of a raw item's meta. */
    fun internalNbt(item: Map<*, *>): MutableMap<String, Any?>? {
        val meta = item["meta"] as? Map<*, *> ?: return null
        val internal = meta["internal"] as? String ?: return null
        return LegacyNbt.readInternal(internal)
    }
}
