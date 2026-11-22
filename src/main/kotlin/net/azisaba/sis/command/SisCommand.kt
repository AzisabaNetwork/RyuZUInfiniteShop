package net.azisaba.sis.command

import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.dialog.NpcSpecs
import net.azisaba.sis.dialog.SearchTarget
import net.azisaba.sis.gui.EditorMenus
import net.azisaba.sis.gui.SearchMenus
import net.azisaba.sis.gui.ShopListMenu
import net.azisaba.sis.item.Items
import net.azisaba.sis.listener.ShopOpener
import net.azisaba.sis.migration.LegacyImporter
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

/** `/sis` (declared in plugin.yml). */
class SisCommand(private val sis: Sis, private val opener: ShopOpener, private val reload: (CommandSender) -> Unit) : TabExecutor {

    private data class Sub(val name: String, val permission: String, val usage: String, val run: (CommandSender, List<String>) -> Unit)

    private val subs: List<Sub> = listOf(
        Sub("search", "sis.search", "search [keyword]") { s, a -> search(s, a) },
        Sub("list", "sis.list", "list") { s, _ -> player(s)?.let { ShopListMenu(sis, it).open() } },
        Sub("settings", "sis.player", "settings") { s, _ -> player(s)?.let { sis.dialogs.playerSettings(it, null) } },
        Sub("create", "sis.admin", "create [villager|mannequin|mythic:<id>|citizens:<id>|block]") { s, a -> create(s, a) },
        Sub("edit", "sis.admin", "edit [shop]") { s, a -> player(s)?.let { p -> shopArg(p, a.getOrNull(0), nearest = true)?.let { EditorMenus.openShopEditor(sis, p, it) } } },
        Sub("open", "sis.admin", "open <shop> [player]") { s, a -> open(s, a) },
        Sub("remove", "sis.admin", "remove <shop>") { s, a -> shopArg(s, a.getOrNull(0))?.let { sis.shops.remove(it, s); Messages.send(s, "editor.shop-deleted", "id" to it.id) } },
        Sub("tp", "sis.admin", "tp <shop>") { s, a -> player(s)?.let { p -> shopArg(p, a.getOrNull(0))?.let { shop -> shop.location.toBukkit()?.let { p.teleportAsync(it) } ?: Messages.send(p, "editor.world-not-loaded") } } },
        Sub("move", "sis.admin", "move <shop>") { s, a -> move(s, a) },
        Sub("searchable", "sis.admin", "searchable <world> <true|false>") { s, a -> searchable(s, a) },
        Sub("limit", "sis.admin", "limit <player> <shop> <trade#|all> <set|add|reset> [count]") { s, a -> limit(s, a) },
        Sub("history", "sis.admin", "history <shop>") { s, a -> history(s, a) },
        Sub("restore", "sis.admin", "restore <shop> <number>") { s, a -> restore(s, a) },
        Sub("mmid", "sis.admin", "mmid [shop]") { s, a -> mmid(s, a) },
        Sub("migrate", "sis.admin", "migrate [folder] [--overwrite]") { s, a -> migrate(s, a) },
        Sub("reload", "sis.admin", "reload") { s, _ -> reload(s) },
        Sub("save", "sis.admin", "save") { s, _ -> sis.repository.flush(); Messages.send(s, "command.saved") },
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.firstOrNull()?.let { name -> subs.firstOrNull { it.name.equals(name, true) } }
        if (sub == null) {
            if (args.isEmpty() && sender is Player && sender.hasPermission("sis.search")) {
                sis.dialogs.search(sender, null)
                return true
            }
            help(sender, label)
            return true
        }
        if (!sender.hasPermission(sub.permission)) {
            Messages.send(sender, "command.no-permission")
            return true
        }
        runCatching { sub.run(sender, args.drop(1)) }.onFailure {
            Messages.send(sender, "command.error", "error" to (it.message ?: it.toString()))
            sis.plugin.logger.log(java.util.logging.Level.WARNING, "/sis ${args.joinToString(" ")}", it)
        }
        return true
    }

    private fun help(sender: CommandSender, label: String) {
        Messages.send(sender, "command.help-header")
        subs.filter { sender.hasPermission(it.permission) }.forEach {
            Messages.send(sender, "command.help-line", "usage" to "/$label ${it.usage}", "description" to Messages.get("command.desc.${it.name}"))
        }
    }

    private fun player(sender: CommandSender): Player? = (sender as? Player).also { if (it == null) Messages.send(sender, "command.player-only") }

    private fun shopArg(sender: CommandSender, id: String?, nearest: Boolean = false): Shop? {
        if (id == null && nearest && sender is Player) {
            val shop = sis.shops.all.filter { it.location.world == sender.world.name }
                .minByOrNull { it.location.toBukkit()?.distanceSquared(sender.location) ?: Double.MAX_VALUE }
                ?.takeIf { (it.location.toBukkit()?.distanceSquared(sender.location) ?: Double.MAX_VALUE) <= 36.0 }
            if (shop == null) Messages.send(sender, "command.no-shop-nearby")
            return shop
        }
        if (id == null) {
            Messages.send(sender, "command.shop-required")
            return null
        }
        return sis.shops.get(id).also { if (it == null) Messages.send(sender, "command.shop-not-found", "id" to id) }
    }

    private fun search(sender: CommandSender, args: List<String>) {
        val p = player(sender) ?: return
        if (args.isEmpty()) sis.dialogs.search(p, null) else SearchMenus.run(sis, p, args.joinToString(" "), SearchTarget.ALL, null)
    }

    private fun create(sender: CommandSender, args: List<String>) {
        val p = player(sender) ?: return
        val spec = if (args.isEmpty()) NpcSpec.Vanilla(org.bukkit.entity.EntityType.VILLAGER) else NpcSpecs.parse(args.joinToString(" "))
        if (spec == null) {
            Messages.send(p, "editor.invalid-npc", "input" to args.joinToString(" "))
            return
        }
        val at = p.location.clone()
        if (spec == NpcSpec.Block) {
            val target = p.getTargetBlockExact(6) ?: return Messages.send(p, "command.look-at-block")
            at.set(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
        }
        at.yaw = Math.round(at.yaw / 45f) * 45f
        at.pitch = 0f
        val location = ShopLocation.of(at)
        val shop = Shop(sis.shops.newId(location), "", location, spec)
        sis.shops.create(shop, p)
        Messages.send(p, "command.created", "id" to shop.id)
        EditorMenus.openShopEditor(sis, p, shop)
    }

    private fun open(sender: CommandSender, args: List<String>) {
        val shop = shopArg(sender, args.getOrNull(0)) ?: return
        val target = args.getOrNull(1)?.let { Bukkit.getPlayerExact(it) ?: return Messages.send(sender, "command.player-not-found", "name" to it) }
            ?: player(sender) ?: return
        opener.open(target, shop, forceTrade = true)
    }

    private fun move(sender: CommandSender, args: List<String>) {
        val p = player(sender) ?: return
        val shop = shopArg(p, args.getOrNull(0)) ?: return
        val at = p.location.clone().apply { yaw = Math.round(yaw / 45f) * 45f; pitch = 0f }
        shop.location = ShopLocation.of(at)
        sis.shops.changed(shop, p, "move", shop.location.describe(), respawn = true)
        Messages.send(p, "command.moved", "id" to shop.id)
    }

    private fun searchable(sender: CommandSender, args: List<String>) {
        val world = args.getOrNull(0) ?: return Messages.send(sender, "command.usage", "usage" to "searchable <world> <true|false>")
        val value = args.getOrNull(1)?.toBooleanStrictOrNull() ?: return Messages.send(sender, "command.usage", "usage" to "searchable <world> <true|false>")
        val shops = sis.shops.all.filter { it.location.world.equals(world, true) }
        shops.forEach { it.searchable = value; sis.shops.changed(it, sender, "searchable", value.toString()) }
        Messages.send(sender, "command.searchable-set", "count" to shops.size, "world" to world, "value" to value)
    }

    private fun limit(sender: CommandSender, args: List<String>) {
        if (args.size < 4) return Messages.send(sender, "command.usage", "usage" to "limit <player> <shop> <trade#|all> <set|add|reset> [count]")
        val target: OfflinePlayer = Bukkit.getPlayerExact(args[0]) ?: Bukkit.getOfflinePlayerIfCached(args[0])
            ?: return Messages.send(sender, "command.player-not-found", "name" to args[0])
        val shop = shopArg(sender, args[1]) ?: return
        val trades: List<Trade> = if (args[2].equals("all", true)) shop.trades.toList() else {
            val n = args[2].toIntOrNull()?.takeIf { it in 1..shop.trades.size } ?: return Messages.send(sender, "command.invalid-trade-number", "max" to shop.trades.size)
            listOf(shop.trades[n - 1])
        }
        val count = args.getOrNull(4)?.toIntOrNull()
        val id = target.uniqueId
        val online = target.isOnline
        val mode = args[3].lowercase()
        if (mode !in setOf("reset", "set", "add")) return Messages.send(sender, "command.usage", "usage" to "limit <player> <shop> <trade#|all> <set|add|reset> [count]")
        if (mode != "reset" && count == null) return Messages.send(sender, "editor.invalid-number")
        val apply = {
            when (mode) {
                "reset" -> sis.counter.resetPlayer(id, trades)
                "set" -> trades.forEach { sis.counter.set(it, id, count!!) }
                else -> trades.forEach { sis.counter.set(it, id, sis.counter.count(it, id) + count!!) }
            }
            Messages.send(sender, "command.limit-changed", "player" to (target.name ?: id.toString()), "count" to trades.size)
            if (!online) sis.counter.unloadPlayer(id)
        }
        // Offline players: read their counts first so "add" adds to the real value.
        if (online || sis.counter.isLoaded(id)) apply()
        else sis.counter.loadPlayerAsync(id).thenRun { Bukkit.getScheduler().runTask(sis.plugin, Runnable { apply() }) }
    }

    private fun history(sender: CommandSender, args: List<String>) {
        val id = args.getOrNull(0) ?: return Messages.send(sender, "command.shop-required")
        val entries = sis.repository.history(id)
        if (entries.isEmpty()) return Messages.send(sender, "command.history-empty", "id" to id)
        Messages.send(sender, "command.history-header", "id" to id, "count" to entries.size)
        entries.forEachIndexed { i, e -> Messages.send(sender, "command.history-line", "number" to i + 1, "label" to e.label) }
    }

    private fun restore(sender: CommandSender, args: List<String>) {
        val id = args.getOrNull(0) ?: return Messages.send(sender, "command.shop-required")
        val entries = sis.repository.history(id)
        val n = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..entries.size }
            ?: return Messages.send(sender, "command.history-invalid", "max" to entries.size)
        val shop = sis.repository.readHistory(id, entries[n - 1])
        sis.shops.replace(shop, sender, "restore")
        Messages.send(sender, "command.restored", "id" to id, "label" to entries[n - 1].label)
    }

    private fun mmid(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            val p = player(sender) ?: return
            Items.explain(p.inventory.itemInMainHand).forEach { sender.sendMessage(Messages.parse("<gray>$it")) }
            return
        }
        val shop = shopArg(sender, args[0]) ?: return
        var shown = 0
        shop.trades.forEachIndexed { i, trade ->
            (trade.costs + trade.rewards).filterIsInstance<net.azisaba.sis.model.TradeEntry.Item>().forEach { entry ->
                if (entry.item.hasItemMeta()) {
                    sender.sendMessage(Messages.parse("<yellow>trade ${i + 1}: <white>${entry.item.type.key.key}"))
                    Items.explain(entry.item).forEach { sender.sendMessage(Messages.parse("<gray>  $it")) }
                    shown++
                }
            }
        }
        if (shown == 0) Messages.send(sender, "command.mmid-ok")
    }

    private fun migrate(sender: CommandSender, args: List<String>) {
        val folder = args.firstOrNull { !it.startsWith("--") }?.let { java.io.File(it) } ?: sis.plugin.dataFolder
        val overwrite = "--overwrite" in args
        Messages.send(sender, "migrate.started", "folder" to folder.path)
        val report = LegacyImporter(sis).import(folder, overwrite)
        Messages.send(sender, "migrate.done", "shops" to report.shops, "trades" to report.trades, "skipped" to report.skipped,
            "failed" to report.failed.size, "counts" to report.counts)
        report.failed.entries.take(20).forEach { (id, error) -> Messages.send(sender, "migrate.failed-line", "id" to id, "error" to error) }
        sis.shops.load()
        sis.npcs.despawnAll()
        sis.npcs.spawnLoaded()
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) return subs.filter { sender.hasPermission(it.permission) }.map { it.name }.filter { it.startsWith(args[0], true) }
        val sub = subs.firstOrNull { it.name.equals(args[0], true) } ?: return emptyList()
        if (!sender.hasPermission(sub.permission)) return emptyList()
        val last = args.last()
        val options: List<String> = when (sub.name) {
            "edit", "open", "remove", "tp", "move", "history", "restore", "mmid" -> if (args.size == 2) sis.shops.ids() else if (sub.name == "open" && args.size == 3) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            "create" -> if (args.size == 2) NpcSpecs.suggestions() else emptyList()
            "searchable" -> when (args.size) { 2 -> Bukkit.getWorlds().map { it.name }; 3 -> listOf("true", "false"); else -> emptyList() }
            "limit" -> when (args.size) {
                2 -> Bukkit.getOnlinePlayers().map { it.name }
                3 -> sis.shops.ids()
                4 -> listOf("all") + (sis.shops.get(args[2])?.trades?.indices?.map { (it + 1).toString() } ?: emptyList())
                5 -> listOf("set", "add", "reset")
                else -> emptyList()
            }
            "migrate" -> listOf("--overwrite")
            else -> emptyList()
        }
        return options.filter { it.startsWith(last, true) }.take(200)
    }
}
