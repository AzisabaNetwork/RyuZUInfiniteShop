package net.azisaba.sis

import net.azisaba.sis.config.PluginConfig
import net.azisaba.sis.dialog.Dialogs
import net.azisaba.sis.gui.Icons
import net.azisaba.sis.npc.NpcManager
import net.azisaba.sis.service.PeriodClock
import net.azisaba.sis.service.ShopService
import net.azisaba.sis.service.TradeCounter
import net.azisaba.sis.service.TradeService
import net.azisaba.sis.storage.Database
import net.azisaba.sis.storage.ShopRepository
import org.bukkit.plugin.java.JavaPlugin

/** Everything the GUIs, dialogs and commands need, wired once in [SearchableInfiniteShop.onEnable]. */
class Sis(val plugin: JavaPlugin) {
    lateinit var config: PluginConfig
    lateinit var db: Database
    lateinit var repository: ShopRepository
    lateinit var clock: PeriodClock
    lateinit var counter: TradeCounter
    lateinit var settings: net.azisaba.sis.service.PlayerSettingsService
    lateinit var shops: ShopService
    lateinit var trades: TradeService
    lateinit var npcs: NpcManager
    lateinit var icons: Icons
    lateinit var dialogs: Dialogs
}
