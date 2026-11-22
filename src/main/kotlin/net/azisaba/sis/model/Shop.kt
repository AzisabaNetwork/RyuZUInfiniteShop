package net.azisaba.sis.model

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType

/** A position that can name a world that is not loaded (yet). */
data class ShopLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) {
    val blockX: Int get() = Math.floor(x).toInt()
    val blockY: Int get() = Math.floor(y).toInt()
    val blockZ: Int get() = Math.floor(z).toInt()
    val chunkX: Int get() = blockX shr 4
    val chunkZ: Int get() = blockZ shr 4

    fun worldOrNull(): World? = Bukkit.getWorld(world)

    fun toBukkit(): Location? = worldOrNull()?.let { Location(it, x, y, z, yaw, pitch) }

    fun isLoaded(): Boolean = worldOrNull()?.isChunkLoaded(chunkX, chunkZ) == true

    fun sameBlock(location: Location): Boolean =
        location.world?.name == world && location.blockX == blockX && location.blockY == blockY && location.blockZ == blockZ

    fun describe(): String = "$world ${blockX}, ${blockY}, $blockZ"

    companion object {
        fun of(location: Location): ShopLocation =
            ShopLocation(location.world.name, location.x, location.y, location.z, location.yaw, location.pitch)
    }
}

/** What stands at the shop. */
sealed interface NpcSpec {
    /**
     * Any vanilla entity (villagers, mannequins, cats, ...). [snapshot] is an [org.bukkit.entity.EntitySnapshot]
     * string holding its full look (variant, profession, equipment, skin, size, ...); null spawns a default one.
     * [equipment] (helmet, chest, legs, boots, main hand, off hand) is put on after spawning; only shops migrated
     * from 1.x have it, a captured look keeps equipment in the snapshot.
     */
    data class Vanilla(
        val type: EntityType,
        val snapshot: String? = null,
        val equipment: List<org.bukkit.inventory.ItemStack?>? = null,
    ) : NpcSpec

    data class Mythic(val mobId: String) : NpcSpec

    /**
     * An existing Citizens NPC that opens this shop when clicked. The NPC itself is managed by Citizens.
     * [ref] is its numeric ID or its UUID (1.x saved UUIDs).
     */
    data class Citizens(val ref: String) : NpcSpec {
        fun matches(id: Int, uuid: java.util.UUID): Boolean = ref == id.toString() || ref.equals(uuid.toString(), ignoreCase = true)
    }

    /** No entity: clicking the block at the shop location opens it. */
    data object Block : NpcSpec
}

class Shop(
    val id: String,
    /** MiniMessage. Empty = no name. */
    var name: String,
    var location: ShopLocation,
    var npc: NpcSpec,
    val trades: MutableList<Trade> = mutableListOf(),
    /** Only players with `sis.locked` can open the shop (1.x "lock"). There is no view-only state. */
    var locked: Boolean = false,
    /** Shows up in /sis search and the shop list. */
    var searchable: Boolean = true,
    var nameVisible: Boolean = true,
    var lookAtPlayer: Boolean = false,
    /** The NPC is invisible (only its name and equipment show), like 1.x "invisible". */
    var invisible: Boolean = false,
) {
    /** Whether [player] may open (and trade in) this shop: it is not locked, or they may pass locks. */
    fun canOpen(player: org.bukkit.permissions.Permissible): Boolean = !locked || player.hasPermission(LOCK_BYPASS_PERMISSION)

    fun trade(id: java.util.UUID): Trade? = trades.firstOrNull { it.id == id }

    fun replaceTrade(trade: Trade) {
        val index = trades.indexOfFirst { it.id == trade.id }
        if (index >= 0) trades[index] = trade else trades += trade
    }

    companion object {
        const val LOCK_BYPASS_PERMISSION = "sis.locked"
    }

    fun copyOf(newId: String = id): Shop = Shop(
        newId, name, location, npc, trades.toMutableList(), locked, searchable, nameVisible, lookAtPlayer, invisible,
    )
}
