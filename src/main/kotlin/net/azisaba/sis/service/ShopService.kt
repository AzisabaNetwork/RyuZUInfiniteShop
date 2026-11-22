package net.azisaba.sis.service

import net.azisaba.sis.config.Messages
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.storage.Database
import net.azisaba.sis.storage.ShopRepository
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Logger

/**
 * Every shop in memory, plus saving and the edit log. All mutations go through here so the NPC, open GUIs, files
 * and logs stay in step: call [changed] after editing a [Shop] object.
 */
class ShopService(
    private val repository: ShopRepository,
    private val db: Database,
    private val logger: Logger,
    private val logEdits: () -> Boolean,
) {
    private val shops = LinkedHashMap<String, Shop>()
    private val tradeOwners = HashMap<UUID, Shop>()

    /** Called after a shop was added, changed or removed (NPC respawn, GUI refresh). */
    val listeners = mutableListOf<ShopChangeListener>()

    val all: Collection<Shop> get() = shops.values

    fun get(id: String): Shop? = shops[id] ?: shops.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value

    fun ids(): List<String> = shops.keys.toList()

    fun ownerOf(tradeId: UUID): Shop? = tradeOwners[tradeId]

    fun byCitizens(npcId: Int, uuid: UUID): Shop? = shops.values.firstOrNull { (it.npc as? NpcSpec.Citizens)?.matches(npcId, uuid) == true }

    fun byBlock(location: org.bukkit.Location): Shop? =
        shops.values.firstOrNull { it.npc == NpcSpec.Block && it.location.sameBlock(location) }

    fun inChunk(world: String, chunkX: Int, chunkZ: Int): List<Shop> =
        shops.values.filter { it.location.world == world && it.location.chunkX == chunkX && it.location.chunkZ == chunkZ }

    fun load(): ShopRepository.LoadResult {
        val result = repository.loadAll()
        shops.clear()
        result.shops.forEach { shops[it.id] = it }
        reindex()
        result.failures.forEach { (id, error) -> logger.severe("Could not load shop $id: ${error.message}") }
        return result
    }

    private fun reindex() {
        tradeOwners.clear()
        for (shop in shops.values) {
            for (trade in shop.trades) {
                val previous = tradeOwners.putIfAbsent(trade.id, shop)
                if (previous != null) {
                    // A copied shop file: give the copy its own trade IDs so limits are counted separately.
                    val index = shop.trades.indexOf(trade)
                    val fresh = trade.copy(id = UUID.randomUUID())
                    shop.trades[index] = fresh
                    tradeOwners[fresh.id] = shop
                    val where = if (previous === shop) "appears twice in ${shop.id}" else "of ${shop.id} is also in ${previous.id}"
                    logger.warning("Trade ${trade.id} $where; the copy got the new ID ${fresh.id}")
                    repository.save(shop)
                }
            }
        }
    }

    /** A free ID from a location: "world_10_64_-3", "world_10_64_-3_2", ... */
    fun newId(location: ShopLocation): String {
        val base = "${location.world}_${location.blockX}_${location.blockY}_${location.blockZ}".replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        if (base !in shops) return base
        var n = 2
        while ("${base}_$n" in shops) n++
        return "${base}_$n"
    }

    fun create(shop: Shop, actor: CommandSender?) {
        require(shop.id !in shops) { "shop ${shop.id} exists" }
        shops[shop.id] = shop
        shop.trades.forEach { tradeOwners[it.id] = shop }
        repository.save(shop)
        logEdit(actor, "create", shop.id, shop.location.describe())
        listeners.forEach { it.added(shop) }
    }

    /**
     * Saves a shop after its object was edited. [respawn] = its NPC must be rebuilt (look/kind/location changed).
     */
    fun changed(shop: Shop, actor: CommandSender?, action: String, detail: String = "", respawn: Boolean = false) {
        tradeOwners.values.removeIf { it === shop }
        shop.trades.forEach { tradeOwners[it.id] = shop }
        repository.save(shop)
        logEdit(actor, action, shop.id, detail)
        listeners.forEach { it.changed(shop, respawn) }
    }

    fun remove(shop: Shop, actor: CommandSender?) {
        shops.remove(shop.id)
        tradeOwners.values.removeIf { it === shop }
        repository.delete(shop.id)
        logEdit(actor, "remove", shop.id, shop.location.describe())
        listeners.forEach { it.removed(shop) }
    }

    /** Replaces a shop by another version of itself (history restore, reload of one file). */
    fun replace(shop: Shop, actor: CommandSender?, action: String) {
        val old = shops[shop.id]
        shops[shop.id] = shop
        tradeOwners.values.removeIf { it === old || it === shop }
        shop.trades.forEach { tradeOwners[it.id] = shop }
        repository.save(shop)
        logEdit(actor, action, shop.id, "")
        if (old != null) listeners.forEach { it.removed(old) }
        listeners.forEach { it.added(shop) }
    }

    fun addTrade(shop: Shop, trade: Trade, actor: CommandSender?) {
        shop.trades += trade
        changed(shop, actor, "add-trade", trade.id.toString())
    }

    fun logEdit(actor: CommandSender?, action: String, shopId: String, detail: String) {
        if (!logEdits()) return
        val (id, name) = when (actor) {
            is Player -> actor.uniqueId.toString() to actor.name
            null -> "-" to "-"
            else -> "console" to actor.name
        }
        db.logEdit(System.currentTimeMillis(), id, name, action, shopId, detail)
    }

    fun displayName(shop: Shop) = if (shop.name.isBlank()) Messages.get("shop.unnamed", "id" to shop.id) else Messages.parse(shop.name)

    fun plainName(shop: Shop): String = Messages.plain(displayName(shop))
}

interface ShopChangeListener {
    fun added(shop: Shop) {}
    fun changed(shop: Shop, respawn: Boolean) {}
    fun removed(shop: Shop) {}
}
