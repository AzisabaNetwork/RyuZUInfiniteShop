package net.azisaba.sis

import net.azisaba.sis.command.SisCommand
import net.azisaba.sis.config.Messages
import net.azisaba.sis.config.PluginConfig
import net.azisaba.sis.dialog.Dialogs
import net.azisaba.sis.gui.Icons
import net.azisaba.sis.gui.MenuListener
import net.azisaba.sis.gui.SearchIndex
import net.azisaba.sis.gui.SearchMenus
import net.azisaba.sis.item.Items
import net.azisaba.sis.item.mythic.MythicInstanceProvider
import net.azisaba.sis.itemize.Itemizer
import net.azisaba.sis.listener.NpcProtectListener
import net.azisaba.sis.listener.PlayerListener
import net.azisaba.sis.listener.ShopInteractListener
import net.azisaba.sis.listener.ShopOpener
import net.azisaba.sis.listener.WorldListener
import net.azisaba.sis.migration.LegacyImporter
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.npc.NpcManager
import net.azisaba.sis.service.PeriodClock
import net.azisaba.sis.service.ShopService
import net.azisaba.sis.service.TradeCounter
import net.azisaba.sis.service.TradeService
import net.azisaba.sis.storage.Database
import net.azisaba.sis.storage.ShopRepository
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class SearchableInfiniteShop : JavaPlugin() {
    private val sis = Sis(this)
    private lateinit var opener: ShopOpener
    private lateinit var searchIndex: SearchIndex

    override fun onEnable() {
        LegacyImporter.moveLegacyConfig(dataFolder)
        saveDefaultConfig()
        applyConfig()
        MythicInstanceProvider.init(this)

        sis.db = Database.open(sis.config, dataFolder, logger)
        sis.clock = PeriodClock(sis.config.zone, sis.config.resetHour)
        sis.counter = TradeCounter(sis.db, sis.clock)
        sis.settings = net.azisaba.sis.service.PlayerSettingsService(sis.db)
        sis.repository = ShopRepository(dataFolder, logger) { sis.config.historyKeep }
        sis.shops = ShopService(sis.repository, sis.db, logger) { sis.config.logEdits }
        sis.trades = TradeService(sis.counter, net.azisaba.sis.service.StoredItems.create(logger), sis.settings::get) { player, shop, trade, times, successes ->
            if (sis.config.logTrades) sis.db.logTrade(System.currentTimeMillis(), player.uniqueId, player.name, shop.id, trade.id, times, successes)
        }
        sis.npcs = NpcManager(this, sis.shops) { sis.config }
        sis.icons = Icons { sis.config }
        sis.dialogs = Dialogs(sis)
        Itemizer.init(sis)
        searchIndex = SearchIndex(sis)
        SearchMenus.index = searchIndex
        sis.shops.listeners += sis.npcs
        sis.shops.listeners += searchIndex
        opener = ShopOpener(sis)

        runCatching { LegacyImporter(sis).autoMigrate(dataFolder) }
            .onSuccess { report ->
                if (report != null) {
                    logger.info("Migrated 1.x data (moved to legacy-1.x/): ${report.shops} shops, ${report.trades} trades, ${report.counts} trade counts, ${report.skipped} skipped, ${report.failed.size} failed")
                    report.failed.forEach { (id, error) -> logger.warning("  not migrated: $id: $error") }
                }
            }
            .onFailure { logger.log(java.util.logging.Level.SEVERE, "Migration of 1.x data failed", it) }

        val loaded = sis.shops.load()
        logger.info("Loaded ${loaded.shops.size} shops" + if (loaded.failures.isNotEmpty()) " (${loaded.failures.size} failed)" else "")
        sis.counter.refreshGlobalBlocking()
        Bukkit.getOnlinePlayers().forEach { p ->
            sis.counter.loadPlayerAsync(p.uniqueId)
            server.scheduler.runTaskAsynchronously(this, Runnable { sis.settings.loadBlocking(p.uniqueId) })
        }

        val pm = server.pluginManager
        pm.registerEvents(MenuListener(), this)
        pm.registerEvents(ShopInteractListener(sis, opener), this)
        pm.registerEvents(NpcProtectListener(sis), this)
        pm.registerEvents(WorldListener(sis), this)
        pm.registerEvents(PlayerListener(sis, opener), this)
        if (pm.getPlugin("MythicMobs") != null) pm.registerEvents(MythicReloadListener(), this)

        val command = SisCommand(sis, opener, ::reload)
        getCommand("searchableinfiniteshop")!!.apply {
            setExecutor(command)
            tabCompleter = command
        }

        // Worlds of other plugins (Multiverse) may load right after us; their chunks spawn NPCs through ChunkLoadEvent.
        server.scheduler.runTask(this, Runnable { sis.npcs.spawnLoaded() })
        server.scheduler.runTaskTimer(this, Runnable { sis.npcs.heal() }, 200L, 100L)
        server.scheduler.runTaskTimer(this, Runnable { sis.npcs.lookAtPlayers() }, 20L, 4L)
        val refresh = sis.config.globalRefreshSeconds * 20
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { sis.counter.refreshGlobal() }, refresh, refresh)
    }

    override fun onDisable() {
        if (!::opener.isInitialized) return
        Bukkit.getOnlinePlayers().forEach { p -> if (p.openInventory.topInventory.holder is net.azisaba.sis.gui.Menu) p.closeInventory() }
        sis.npcs.despawnAll()
        runCatching { sis.repository.close() }.onFailure { logger.severe("Could not write shops: $it") }
        runCatching { sis.db.close() }
    }

    private fun applyConfig() {
        reloadConfig()
        sis.config = PluginConfig.load(config)
        Messages.load(this, sis.config.language)
        Items.mythicStrict = sis.config.mythicStrict
        Items.isExcluded = sis.config::isMythicExcluded
    }

    private fun reload(sender: CommandSender) {
        Bukkit.getOnlinePlayers().forEach { p -> if (p.openInventory.topInventory.holder is net.azisaba.sis.gui.Menu) p.closeInventory() }
        // storage.* and limits.* need a restart; everything else is applied here.
        applyConfig()
        sis.repository.flush()
        Items.clearCache()
        searchIndex.clear()
        sis.npcs.despawnAll()
        val result = sis.shops.load()
        sis.npcs.spawnLoaded()
        Messages.send(sender, "command.reloaded", "shops" to result.shops.size, "failed" to result.failures.size)
    }

    /** MythicMobs reloaded its items and mobs: forget generated items, respawn MythicMobs NPCs. */
    private inner class MythicReloadListener : Listener {
        @EventHandler
        fun onReload(@Suppress("UNUSED_PARAMETER") event: io.lumine.mythic.bukkit.events.MythicReloadedEvent) {
            Items.clearCache()
            searchIndex.clear()
            sis.shops.all.filter { it.npc is NpcSpec.Mythic }.forEach { sis.npcs.spawn(it) }
        }
    }
}
