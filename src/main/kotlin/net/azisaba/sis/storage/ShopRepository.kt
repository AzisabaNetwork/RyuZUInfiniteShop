package net.azisaba.sis.storage

import net.azisaba.sis.model.Shop
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Shop files (`shops/<id>.yml`) and their history (`history/<id>/<time>.yml.gz`).
 *
 * The YAML text is built on the calling (main) thread, files are written on a single background thread in order,
 * so a burst of edits never writes out of order and never blocks the server.
 */
class ShopRepository(dataFolder: File, private val logger: Logger, private val historyKeep: () -> Int) {

    val shopDir = File(dataFolder, "shops")
    val historyDir = File(dataFolder, "history")
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "SIS-ShopWriter").apply { isDaemon = true } }

    data class LoadResult(val shops: List<Shop>, val failures: Map<String, Throwable>)

    data class HistoryEntry(val file: File, val label: String)

    fun loadAll(): LoadResult {
        shopDir.mkdirs()
        val shops = ArrayList<Shop>()
        val failures = LinkedHashMap<String, Throwable>()
        shopDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.sortedBy { it.name }?.forEach { file ->
            val id = file.name.removeSuffix(".yml")
            // Other files people left in the folder (1.x had a save.yml there) are not shops.
            if (file.useLines { lines -> lines.none { it.startsWith("format:") } }) {
                logger.info("Ignoring shops/${file.name}: not a 2.x shop file")
                return@forEach
            }
            runCatching { load(id, file) }
                .onSuccess { shops += it }
                .onFailure { failures[id] = it }
        }
        return LoadResult(shops, failures)
    }

    fun load(id: String, file: File = File(shopDir, "$id.yml")): Shop {
        val yaml = YamlConfiguration()
        yaml.load(file)
        return ShopYaml.read(id, yaml)
    }

    fun save(shop: Shop) {
        val text = ShopYaml.toYaml(shop).saveToString()
        val id = shop.id
        writer.execute { runCatching { writeNow(id, text) }.onFailure { logger.severe("Could not save shop $id: $it") } }
    }

    fun saveBlocking(shop: Shop) = writeNow(shop.id, ShopYaml.toYaml(shop).saveToString())

    fun delete(id: String) {
        writer.execute {
            val file = File(shopDir, "$id.yml")
            if (file.exists()) {
                archive(id, file, "deleted")
                file.delete()
            }
        }
    }

    /** Waits until every queued write is on disk (shutdown, reload). */
    fun flush() {
        writer.submit {}.get()
    }

    fun close() {
        flush()
        writer.shutdown()
    }

    fun history(id: String): List<HistoryEntry> =
        File(historyDir, id).listFiles { f -> f.name.endsWith(".yml.gz") }
            ?.sortedBy { it.name }
            ?.map { HistoryEntry(it, it.name.removeSuffix(".yml.gz")) }
            .orEmpty()

    /** Reads an old version; the caller decides whether to put it back with [save]. */
    fun readHistory(id: String, entry: HistoryEntry): Shop {
        val text = GZIPInputStream(entry.file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        val yaml = YamlConfiguration()
        yaml.loadFromString(text)
        return ShopYaml.read(id, yaml)
    }

    private fun writeNow(id: String, text: String) {
        shopDir.mkdirs()
        val file = File(shopDir, "$id.yml")
        if (file.exists()) {
            if (file.readText(Charsets.UTF_8) == text) return
            // One history entry per editing session, not per click: skip when the last one is under a minute old.
            val newest = File(historyDir, id).listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L
            if (System.currentTimeMillis() - newest > 60_000L) archive(id, file, null)
        }
        val tmp = File(shopDir, "$id.yml.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun archive(id: String, file: File, suffix: String?) {
        val keep = historyKeep()
        if (keep <= 0) return
        val dir = File(historyDir, id).apply { mkdirs() }
        val name = LocalDateTime.now().format(STAMP) + (suffix?.let { "-$it" } ?: "")
        var target = File(dir, "$name.yml.gz")
        var n = 1
        while (target.exists()) {
            target = File(dir, "$name-${n++}.yml.gz")
        }
        GZIPOutputStream(target.outputStream()).use { out -> file.inputStream().use { it.copyTo(out) } }
        dir.listFiles { f -> f.name.endsWith(".yml.gz") }?.sortedBy { it.name }?.dropLast(keep)?.forEach { it.delete() }
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]+")

        /** IDs made by [net.azisaba.sis.service.ShopService.newId] (no path separators or "..") */
        fun isValidId(id: String): Boolean = ID.matches(id)

        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
