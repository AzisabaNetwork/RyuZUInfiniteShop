package net.azisaba.sis.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.azisaba.sis.config.PluginConfig
import net.azisaba.sis.config.StorageType
import java.io.File
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

/**
 * SQLite or MariaDB, for data written often: trade counts (limits), trade/edit logs and per-player flags.
 * Shop definitions stay in YAML ([ShopRepository]).
 *
 * All queries run on one background thread ([async]); nothing here may be called on the main thread except
 * through [async].
 */
class Database private constructor(
    private val source: HikariDataSource,
    val type: StorageType,
    prefix: String,
    private val logger: Logger,
) : AutoCloseable {
    val counts = "${prefix}trade_counts"
    val tradeLog = "${prefix}trade_log"
    val editLog = "${prefix}edit_log"
    val flags = "${prefix}player_flags"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "SIS-Database").apply { isDaemon = true } }

    fun <T> async(block: (Connection) -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({
        source.connection.use(block)
    }, executor).whenComplete { _, error -> if (error != null) logger.log(Level.WARNING, "Database error", error) }

    /** Runs on the calling thread. Only for startup, shutdown and async events (pre-login). */
    fun <T> blocking(block: (Connection) -> T): T = source.connection.use(block)

    private fun createTables() = blocking { c ->
        val autoId = if (type == StorageType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "BIGINT AUTO_INCREMENT PRIMARY KEY"
        c.createStatement().use { st ->
            st.execute(
                """CREATE TABLE IF NOT EXISTS $counts (
                    scope_key VARCHAR(36) NOT NULL,
                    trade_id CHAR(36) NOT NULL,
                    period VARCHAR(16) NOT NULL,
                    count INT NOT NULL,
                    PRIMARY KEY (scope_key, trade_id, period))""".trimIndent(),
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS $tradeLog (
                    id $autoId,
                    time BIGINT NOT NULL,
                    player CHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    shop_id VARCHAR(128) NOT NULL,
                    trade_id CHAR(36) NOT NULL,
                    times INT NOT NULL,
                    successes INT NOT NULL)""".trimIndent(),
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS $editLog (
                    id $autoId,
                    time BIGINT NOT NULL,
                    actor VARCHAR(36) NOT NULL,
                    actor_name VARCHAR(32) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    shop_id VARCHAR(128) NOT NULL,
                    detail TEXT NOT NULL)""".trimIndent(),
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS $flags (
                    player CHAR(36) NOT NULL,
                    flag VARCHAR(64) NOT NULL,
                    PRIMARY KEY (player, flag))""".trimIndent(),
            )
        }
    }

    /** `count = count + delta` (insert when missing). */
    fun upsertAddSql(): String = when (type) {
        StorageType.SQLITE -> "INSERT INTO $counts (scope_key, trade_id, period, count) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(scope_key, trade_id, period) DO UPDATE SET count = count + excluded.count"
        StorageType.MARIADB -> "INSERT INTO $counts (scope_key, trade_id, period, count) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE count = count + VALUES(count)"
    }

    /** `count = value` (insert when missing). */
    fun upsertSetSql(): String = when (type) {
        StorageType.SQLITE -> "INSERT INTO $counts (scope_key, trade_id, period, count) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(scope_key, trade_id, period) DO UPDATE SET count = excluded.count"
        StorageType.MARIADB -> "INSERT INTO $counts (scope_key, trade_id, period, count) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE count = VALUES(count)"
    }

    fun insertFlagSql(): String = when (type) {
        StorageType.SQLITE -> "INSERT OR IGNORE INTO $flags (player, flag) VALUES (?, ?)"
        StorageType.MARIADB -> "INSERT IGNORE INTO $flags (player, flag) VALUES (?, ?)"
    }

    fun hasFlagBlocking(player: UUID, flag: String): Boolean = blocking { c ->
        c.prepareStatement("SELECT 1 FROM $flags WHERE player = ? AND flag = ?").use { ps ->
            ps.setString(1, player.toString()); ps.setString(2, flag)
            ps.executeQuery().use { it.next() }
        }
    }

    fun setFlag(player: UUID, flag: String): CompletableFuture<Unit> = async { c ->
        c.prepareStatement(insertFlagSql()).use { ps ->
            ps.setString(1, player.toString()); ps.setString(2, flag); ps.executeUpdate()
        }
        Unit
    }

    fun removeFlag(player: UUID, flag: String): CompletableFuture<Unit> = async { c ->
        c.prepareStatement("DELETE FROM $flags WHERE player = ? AND flag = ?").use { ps ->
            ps.setString(1, player.toString()); ps.setString(2, flag); ps.executeUpdate()
        }
        Unit
    }

    /** Every flag of a player. Runs on the database thread, after writes still queued. */
    fun flagsBlocking(player: UUID): Set<String> = async { c ->
        c.prepareStatement("SELECT flag FROM $flags WHERE player = ?").use { ps ->
            ps.setString(1, player.toString())
            ps.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
        }
    }.get()

    fun logTrade(time: Long, player: UUID, name: String, shopId: String, tradeId: UUID, times: Int, successes: Int) = async { c ->
        c.prepareStatement("INSERT INTO $tradeLog (time, player, player_name, shop_id, trade_id, times, successes) VALUES (?, ?, ?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, time); ps.setString(2, player.toString()); ps.setString(3, name.take(16))
            ps.setString(4, shopId.take(128)); ps.setString(5, tradeId.toString()); ps.setInt(6, times); ps.setInt(7, successes)
            ps.executeUpdate()
        }
    }

    fun logEdit(time: Long, actor: String, actorName: String, action: String, shopId: String, detail: String) = async { c ->
        c.prepareStatement("INSERT INTO $editLog (time, actor, actor_name, action, shop_id, detail) VALUES (?, ?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, time); ps.setString(2, actor.take(36)); ps.setString(3, actorName.take(32))
            ps.setString(4, action.take(32)); ps.setString(5, shopId.take(128)); ps.setString(6, detail)
            ps.executeUpdate()
        }
    }

    override fun close() {
        executor.shutdown()
        runCatching { executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS) }
        source.close()
    }

    companion object {
        fun open(config: PluginConfig, dataFolder: File, logger: Logger): Database {
            val hikari = HikariConfig()
            hikari.poolName = "SIS"
            when (config.storageType) {
                StorageType.SQLITE -> {
                    Class.forName("org.sqlite.JDBC")
                    hikari.jdbcUrl = "jdbc:sqlite:" + File(dataFolder, "data.db").absolutePath
                    hikari.maximumPoolSize = 1
                    hikari.connectionInitSql = "PRAGMA journal_mode=WAL"
                }
                StorageType.MARIADB -> {
                    Class.forName("org.mariadb.jdbc.Driver")
                    val m = config.mariadb
                    hikari.jdbcUrl = "jdbc:mariadb://${m.host}:${m.port}/${m.database}"
                    hikari.username = m.user
                    hikari.password = m.password
                    hikari.maximumPoolSize = m.poolSize
                }
            }
            val db = Database(HikariDataSource(hikari), config.storageType, config.tablePrefix, logger)
            db.createTables()
            return db
        }

        /** For tests. */
        fun openSqlite(file: File, prefix: String, logger: Logger): Database {
            val hikari = HikariConfig()
            hikari.jdbcUrl = "jdbc:sqlite:" + file.absolutePath
            hikari.maximumPoolSize = 1
            val db = Database(HikariDataSource(hikari), StorageType.SQLITE, prefix, logger)
            db.createTables()
            return db
        }
    }
}
