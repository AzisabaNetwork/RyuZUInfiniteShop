package net.azisaba.sis.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

/**
 * Message lookup. lang/<language>.yml in the data folder overrides the bundled file of the same language, which
 * overrides the bundled Japanese file. Values are MiniMessage; placeholders are passed as `"name" to value`
 * (a [Component] is inserted as is, anything else as unparsed text).
 */
object Messages {
    private val mm = MiniMessage.miniMessage()
    private var values: Map<String, String> = emptyMap()
    private var prefix: String = ""

    fun load(plugin: JavaPlugin, language: String) {
        val merged = LinkedHashMap<String, String>()
        fun absorb(yaml: YamlConfiguration) {
            for (key in yaml.getKeys(true)) {
                if (yaml.isString(key)) merged[key] = yaml.getString(key)!!
                else if (yaml.isList(key)) merged[key] = yaml.getStringList(key).joinToString("\n")
            }
        }
        fun bundled(lang: String): YamlConfiguration? = plugin.getResource("lang/$lang.yml")?.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8))
        }
        bundled("ja")?.let(::absorb)
        if (language != "ja") bundled(language)?.let(::absorb)
        for (lang in listOf("ja", "en")) {
            if (plugin.getResource("lang/$lang.yml") != null && !File(plugin.dataFolder, "lang/$lang.yml").exists()) {
                plugin.saveResource("lang/$lang.yml", false)
            }
        }
        val file = File(plugin.dataFolder, "lang/$language.yml")
        if (file.exists()) absorb(YamlConfiguration.loadConfiguration(file))
        values = merged
        prefix = merged["prefix"] ?: ""
    }

    /** For tests: use these values directly. */
    fun loadValues(map: Map<String, String>) {
        values = map
        prefix = map["prefix"] ?: ""
    }

    fun raw(key: String): String = values[key] ?: key

    fun resolver(vararg placeholders: Pair<String, Any?>): TagResolver = TagResolver.resolver(placeholders.map { (name, value) ->
        when (value) {
            is Component -> Placeholder.component(name, value)
            else -> Placeholder.unparsed(name, value?.toString() ?: "")
        }
    })

    /** A message as a component. Italic is switched off so it can be used in item names and lore. */
    fun get(key: String, vararg placeholders: Pair<String, Any?>): Component =
        mm.deserialize(raw(key), resolver(*placeholders)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    /** Multi-line message (lines separated by "\n" or given as a YAML list), e.g. item lore. */
    fun lines(key: String, vararg placeholders: Pair<String, Any?>): List<Component> {
        val text = raw(key)
        if (text.isEmpty()) return emptyList()
        val resolver = resolver(*placeholders)
        return text.split('\n').map { mm.deserialize(it, resolver).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) }
    }

    fun send(to: CommandSender, key: String, vararg placeholders: Pair<String, Any?>) {
        to.sendMessage(mm.deserialize(prefix + raw(key), resolver(*placeholders)))
    }

    /** Parses a template from config (GUI titles) with placeholders. */
    fun parseTemplate(template: String, vararg placeholders: Pair<String, Any?>): Component =
        mm.deserialize(template, resolver(*placeholders))

    /** Parses free MiniMessage text (shop names etc.). */
    fun parse(text: String): Component =
        mm.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun plain(component: Component): String =
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
}
