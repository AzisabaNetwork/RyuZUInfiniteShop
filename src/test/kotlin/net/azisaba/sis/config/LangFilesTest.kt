package net.azisaba.sis.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class LangFilesTest {
    private fun load(lang: String): YamlConfiguration =
        javaClass.classLoader.getResourceAsStream("lang/$lang.yml")!!.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }

    /** Message keys used in the code (`Messages.get("…")` etc.). Dynamic ones (status.<x>) are not listed. */
    private fun keysInCode(): Set<String> {
        // Keys always have a dot ("shop.no-trades"); placeholder names ("left") do not.
        val pattern = Regex("""Messages\.(?:get|lines|send|raw)\((?:\w+, )?"([a-z0-9\-]+\.[a-z0-9.\-]+)"""")
        return File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] } }.toSet()
    }

    @Test
    fun `no key is read as a boolean`() {
        // YAML reads keys like yes / no / on / off as true / false, so such messages are never found.
        for (lang in listOf("ja", "en")) {
            val bad = load(lang).getKeys(true).filter { key -> key.split('.').any { it == "true" || it == "false" } }
            assertTrue(bad.isEmpty(), "$lang.yml has keys YAML reads as booleans: $bad")
        }
    }

    @Test
    fun `every key used in the code exists in ja yml`() {
        val ja = load("ja")
        val missing = keysInCode().filter { !ja.contains(it) }
        assertTrue(missing.isEmpty(), "missing in ja.yml: $missing")
    }

    @Test
    fun `en yml has the same keys as ja yml`() {
        val ja = load("ja").getKeys(true)
        val en = load("en").getKeys(true)
        assertTrue((ja - en).isEmpty(), "missing in en.yml: ${ja - en}")
    }
}
