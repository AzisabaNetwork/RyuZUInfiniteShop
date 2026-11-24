package net.azisaba.sis.listener

import com.destroystokyo.paper.event.entity.EntityPathfindEvent
import io.papermc.paper.event.player.PlayerTradeEvent
import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.gui.CaptureRequests
import net.azisaba.sis.gui.EditorMenus
import net.azisaba.sis.gui.TradeMenu
import net.azisaba.sis.itemize.Itemizer
import net.azisaba.sis.itemize.LegacyGems
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.event.entity.VillagerCareerChangeEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerShearEntityEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Opening shops: the tutorial on first use, then the trade screen (admins sneaking get the editor). */
class ShopOpener(private val sis: Sis) {
    /** Players who already saw the tutorial this session. */
    val tutorialShown: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun open(player: Player, shop: Shop, forceTrade: Boolean = false) {
        if (net.azisaba.sis.gui.EditLocks.refuse(player, shop)) return
        val admin = player.hasPermission("sis.admin")
        if (admin && player.isSneaking && !forceTrade) {
            EditorMenus.openShopEditor(sis, player, shop)
            return
        }
        if (TradeMenu.refuseOpen(player, shop, bypassLock = forceTrade)) return
        val show = { TradeMenu(sis, player, shop, adminView = admin, unlocked = forceTrade || shop.canOpen(player)).open() }
        val afterLoad = {
            val wants = sis.config.tutorial && !sis.settings[player.uniqueId].tutorialHidden
            if (wants && tutorialShown.add(player.uniqueId)) sis.dialogs.tutorial(player, show) else show()
        }
        if (sis.counter.isLoaded(player.uniqueId)) afterLoad()
        else sis.counter.loadPlayerAsync(player.uniqueId).whenComplete { _, error ->
            Bukkit.getScheduler().runTask(sis.plugin, Runnable {
                if (error == null) afterLoad() else {
                    sis.plugin.logger.warning("Could not read trade counts of ${player.name}: $error")
                    Messages.send(player, "shop.load-failed")
                }
            })
        }
    }
}

class ShopInteractListener(private val sis: Sis, private val opener: ShopOpener) : Listener {

    private fun citizensShop(entity: Entity): Shop? {
        if (!entity.hasMetadata("NPC") || !Bukkit.getPluginManager().isPluginEnabled("Citizens")) return null
        val npc = runCatching { net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity) }.getOrNull() ?: return null
        return sis.shops.byCitizens(npc.id, npc.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            if (sis.npcs.isShopEntity(event.rightClicked)) event.isCancelled = true
            return
        }
        val player = event.player
        CaptureRequests.take(player)?.let { shopId ->
            event.isCancelled = true
            val shop = sis.shops.get(shopId) ?: return
            // A Citizens NPC is not copied but linked: clicking it opens the shop (1.x "register by clicking").
            net.azisaba.sis.npc.CitizensBridge.specOf(event.rightClicked)?.let { citizens ->
                shop.npc = citizens
                sis.shops.changed(shop, player, "citizens", citizens.ref, respawn = true)
                Messages.send(player, "editor.citizens-linked", "id" to citizens.ref)
                return
            }
            val spec = sis.npcs.capture(event.rightClicked)
            if (spec == null) Messages.send(player, "editor.capture-failed") else {
                shop.npc = spec
                sis.shops.changed(shop, player, "capture", event.rightClicked.type.key.key, respawn = true)
                Messages.send(player, "editor.captured", "type" to event.rightClicked.type.key.key)
            }
            return
        }
        val shop = sis.npcs.shopOf(event.rightClicked) ?: citizensShop(event.rightClicked) ?: return
        event.isCancelled = true
        if (handleItemized(player, shop)) return
        opener.open(player, shop)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteractAt(event: PlayerInteractAtEntityEvent) {
        if (sis.npcs.isShopEntity(event.rightClicked)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteractBlock(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        val player = event.player
        val item = event.item
        val shopItem = item?.persistentDataContainer?.has(Itemizer.shopKey) == true
        if (player.isSneaking && player.hasPermission("sis.admin") && (shopItem || LegacyGems.isLegacy(item))) {
            event.isCancelled = true
            val at = block.getRelative(event.blockFace).location.add(0.5, 0.0, 0.5)
            at.yaw = (player.location.yaw + 180f).let { Math.round(it / 45f) * 45f }.let { if (it > 180f) it - 360f else it }
            val location = ShopLocation.of(at)
            val shop = runCatching {
                if (shopItem) Itemizer.readShop(item, sis.shops.newId(location), location)
                else LegacyGems.read(sis, item)?.let { LegacyGems.shop(sis, it, sis.shops.newId(location), location) }
            }.getOrElse {
                Messages.send(player, "itemize.unreadable", "error" to (it.message ?: it.toString()))
                return
            } ?: return
            sis.shops.create(shop, player)
            Messages.send(player, "itemize.placed", "id" to shop.id)
            return
        }
        val shop = sis.shops.byBlock(block.location) ?: return
        event.isCancelled = true
        if (handleItemized(player, shop)) return
        opener.open(player, shop)
    }

    /** Admin right-clicks a shop with a trade/shop item: its trades are added. */
    private fun handleItemized(player: Player, shop: Shop): Boolean {
        val item = player.inventory.itemInMainHand
        if (!player.hasPermission("sis.admin")) return false
        val current = Itemizer.isItemized(item)
        if (!current && !LegacyGems.isLegacy(item)) return false
        val trades = runCatching {
            if (current) Itemizer.readTrade(item)?.let(::listOf) ?: Itemizer.readShop(item, shop.id, shop.location)?.trades
            else LegacyGems.read(sis, item)?.trades?.takeIf { it.isNotEmpty() }
        }.getOrElse {
            Messages.send(player, "itemize.unreadable", "error" to (it.message ?: it.toString()))
            return true
        } ?: return false
        shop.trades += trades
        sis.shops.changed(shop, player, "merge", "${trades.size} trades")
        Messages.send(player, "itemize.merged", "count" to trades.size, "shop" to sis.shops.displayName(shop))
        return true
    }
}

/** Shop NPCs do not take damage, move, burn, change job, pick things up, ... */
class NpcProtectListener(private val sis: Sis) : Listener {
    private fun isShop(entity: Entity) = sis.npcs.isShopEntity(entity)

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onCombust(event: EntityCombustEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onTransform(event: EntityTransformEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onCareer(event: VillagerCareerChangeEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onAcquireTrade(event: VillagerAcquireTradeEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onVanillaTrade(event: PlayerTradeEvent) { if (isShop(event.villager)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onShear(event: PlayerShearEntityEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onLeash(event: PlayerLeashEntityEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onTeleport(event: EntityTeleportEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onCreeperPower(event: org.bukkit.event.entity.CreeperPowerEvent) { if (isShop(event.entity)) event.isCancelled = true }

    @EventHandler(ignoreCancelled = true)
    fun onPathfind(event: EntityPathfindEvent) { if (isShop(event.entity)) event.isCancelled = true }
}

class WorldListener(private val sis: Sis) : Listener {
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val world = event.world.name
        val x = event.chunk.x
        val z = event.chunk.z
        // Next tick: spawning entities while the chunk is still being loaded is not allowed.
        Bukkit.getScheduler().runTask(sis.plugin, Runnable { if (event.world.isChunkLoaded(x, z)) sis.npcs.onChunkLoad(world, x, z) })
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) = sis.npcs.onChunkUnload(event.world.name, event.chunk.x, event.chunk.z)

    /** Leftover shop entities (from a crash of an older version, or MythicMobs keeping them) are removed. */
    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        for (entity in event.entities) {
            if (entity.persistentDataContainer.has(sis.npcs.shopKey) && sis.npcs.shopOf(entity)?.let { sis.npcs.entityOf(it) == entity } != true) entity.remove()
        }
    }
}

class PlayerListener(private val sis: Sis, private val opener: ShopOpener) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        runCatching {
            sis.counter.loadPlayerBlocking(event.uniqueId)
            sis.settings.loadBlocking(event.uniqueId)
        }.onFailure { sis.plugin.logger.warning("Could not read trade counts of ${event.name}: $it") }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        if (!sis.counter.isLoaded(id)) sis.counter.loadPlayerAsync(id)
        // Logging in again from elsewhere: the old session's quit ran after this session's pre-login and dropped
        // the settings (Storage Box use would silently fall back to the defaults).
        if (!sis.settings.isLoaded(id)) Bukkit.getScheduler().runTaskAsynchronously(sis.plugin, Runnable {
            runCatching { sis.settings.loadBlocking(id) }.onFailure { sis.plugin.logger.warning("Could not read the settings of ${event.player.name}: $it") }
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val id = event.player.uniqueId
        CaptureRequests.take(event.player)
        net.azisaba.sis.gui.EditLocks.quit(event.player)
        // Counts may still be queued for writing; dropping them from memory is safe (writes do not need the cache).
        sis.counter.unloadPlayer(id)
        opener.tutorialShown -= id
        sis.settings.unload(id)
    }
}
