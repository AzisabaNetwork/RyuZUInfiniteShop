package net.azisaba.sis.service

import net.azisaba.sis.model.LimitScope
import net.azisaba.sis.model.Trade
import net.azisaba.sis.storage.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * How often each trade was done, per player or server-wide, per period ([PeriodClock]).
 *
 * Reads come from memory so the GUI never waits on the database: a player's rows are read when they log in
 * (pre-login, off the main thread), server-wide rows at startup and then every few seconds. Writes update memory at
 * once and the database in the background as `count = count + n`, so servers sharing MariaDB never overwrite each
 * other.
 */
class TradeCounter(private val db: Database, private val clock: PeriodClock) {
    data class Key(val scope: String, val trade: UUID, val period: String)

    private val counts = ConcurrentHashMap<Key, Int>()
    private val loadedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun scopeOf(trade: Trade, player: UUID): String =
        if (trade.options.limitScope == LimitScope.GLOBAL) GLOBAL else player.toString()

    fun keyOf(trade: Trade, player: UUID): Key = Key(scopeOf(trade, player), trade.id, clock.key(trade.options.reset))

    fun count(trade: Trade, player: UUID): Int = counts[keyOf(trade, player)] ?: 0

    /** Trades left before the limit, or [Int.MAX_VALUE] without a limit. */
    fun remaining(trade: Trade, player: UUID): Int =
        if (!trade.options.hasLimit) Int.MAX_VALUE else (trade.options.limit - count(trade, player)).coerceAtLeast(0)

    fun add(trade: Trade, player: UUID, times: Int) {
        if (times <= 0) return
        val key = keyOf(trade, player)
        counts.merge(key, times, Int::plus)
        db.async { c ->
            c.prepareStatement(db.upsertAddSql()).use { ps ->
                ps.setString(1, key.scope); ps.setString(2, key.trade.toString()); ps.setString(3, key.period); ps.setInt(4, times)
                ps.executeUpdate()
            }
        }
    }

    /** Sets the count of the current period (admin command). */
    fun set(trade: Trade, player: UUID, value: Int): CompletableFuture<*> {
        val key = keyOf(trade, player)
        counts[key] = value.coerceAtLeast(0)
        return db.async { c ->
            c.prepareStatement(db.upsertSetSql()).use { ps ->
                ps.setString(1, key.scope); ps.setString(2, key.trade.toString()); ps.setString(3, key.period); ps.setInt(4, value.coerceAtLeast(0))
                ps.executeUpdate()
            }
        }
    }

    /** Forgets every count of the player in every period (admin command). */
    fun resetPlayer(player: UUID, trades: Collection<Trade>): CompletableFuture<*> {
        val ids = trades.map { it.id }.toSet()
        counts.keys.removeIf { it.scope == player.toString() && it.trade in ids }
        return db.async { c ->
            c.prepareStatement("DELETE FROM ${db.counts} WHERE scope_key = ? AND trade_id = ?").use { ps ->
                for (id in ids) {
                    ps.setString(1, player.toString()); ps.setString(2, id.toString()); ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    /**
     * Blocking: call from AsyncPlayerPreLoginEvent or a background thread. Runs on the database thread, after any
     * write still queued from the player's last session.
     */
    fun loadPlayerBlocking(player: UUID) {
        val rows = db.async { c -> readRows(c, player.toString()) }.get()
        counts.keys.removeIf { it.scope == player.toString() }
        counts.putAll(rows)
        loadedPlayers += player
    }

    fun loadPlayerAsync(player: UUID): CompletableFuture<*> = db.async { c -> readRows(c, player.toString()) }.thenAccept { rows ->
        counts.keys.removeIf { it.scope == player.toString() }
        counts.putAll(rows)
        loadedPlayers += player
    }

    fun unloadPlayer(player: UUID) {
        loadedPlayers -= player
        counts.keys.removeIf { it.scope == player.toString() }
    }

    fun isLoaded(player: UUID): Boolean = player in loadedPlayers

    fun refreshGlobal(): CompletableFuture<*> = db.async { c -> readRows(c, GLOBAL) }.thenAccept { rows ->
        // Keep local increments that may not have reached the database yet: take the larger value.
        rows.forEach { (k, v) -> counts.merge(k, v) { old, new -> maxOf(old, new) } }
    }

    fun refreshGlobalBlocking() {
        counts.putAll(db.blocking { c -> readRows(c, GLOBAL) })
    }

    private fun readRows(c: java.sql.Connection, scope: String): Map<Key, Int> {
        val result = HashMap<Key, Int>()
        c.prepareStatement("SELECT trade_id, period, count FROM ${db.counts} WHERE scope_key = ?").use { ps ->
            ps.setString(1, scope)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val id = runCatching { UUID.fromString(rs.getString(1)) }.getOrNull() ?: continue
                    result[Key(scope, id, rs.getString(2))] = rs.getInt(3)
                }
            }
        }
        return result
    }

    /** Copies 1.x counts (always "all" period) into the database. */
    fun importLegacyBlocking(rows: List<Triple<UUID, UUID, Int>>) {
        db.blocking { c ->
            c.autoCommit = false
            c.prepareStatement(db.upsertSetSql()).use { ps ->
                for ((player, trade, count) in rows) {
                    ps.setString(1, player.toString()); ps.setString(2, trade.toString()); ps.setString(3, "all"); ps.setInt(4, count)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
            c.autoCommit = true
        }
    }

    companion object {
        const val GLOBAL = "*"
    }
}
