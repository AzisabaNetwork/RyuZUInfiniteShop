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
        val dir = File(plugin.dataFolder, "lang")
        for (lang in listOf("ja", "en")) {
            // Reference copy of the current texts, rewritten every start (editing it has no effect)
            plugin.getResource("lang/$lang.yml")?.use { input ->
                File(dir, "default").mkdirs()
                File(dir, "default/$lang.yml").outputStream().use { input.copyTo(it) }
            }
            retireFullCopy(plugin, File(dir, "$lang.yml"))
            val overrides = File(dir, "$lang.yml")
            if (!overrides.exists()) overrides.writeText(OVERRIDES_TEMPLATE.replace("<lang>", lang), Charsets.UTF_8)
        }
        // Only what an admin wrote in lang/<language>.yml replaces the bundled texts
        val file = File(dir, "$language.yml")
        if (file.exists()) absorb(YamlConfiguration.loadConfiguration(file))
        values = merged
        prefix = merged["prefix"] ?: ""
    }

    /**
     * Early 2.0 builds copied the whole bundled file to lang/<lang>.yml, which then kept old texts after updates.
     * Such a copy (no override marker) is renamed to .old once; the bundled texts are used instead.
     */
    private fun retireFullCopy(plugin: JavaPlugin, file: File) {
        if (!file.exists() || file.useLines { lines -> lines.any { it.trim() == OVERRIDES_MARKER } }) return
        val old = File(file.parentFile, file.name + ".old")
        if (old.exists()) old.delete()
        if (file.renameTo(old)) plugin.logger.info("lang/${file.name} was a full copy of old messages; moved to ${old.name} (put only the texts you want to change in lang/${file.name})")
    }

    private const val OVERRIDES_MARKER = "# sis-message-overrides"

    private val OVERRIDES_TEMPLATE = """
        $OVERRIDES_MARKER
        # Texts written here replace the bundled ones. Write only what you want to change; everything else follows
        # plugin updates. All keys and the current texts: lang/default/<lang>.yml (rewritten every start).
        # ここに書いたメッセージだけが標準の文に置き換わります。変えたいキーだけ書いてください。
        # 全キーと現在の標準の文は lang/default/<lang>.yml にあります（起動ごとに更新）。
        #
        # Example / 例:
        # shop:
        #   no-trades: "<red>まだ取引がありません"
    """.trimIndent() + "\n"

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
