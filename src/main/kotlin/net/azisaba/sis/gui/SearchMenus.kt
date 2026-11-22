package net.azisaba.sis.gui

import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.dialog.SearchTarget
import net.azisaba.sis.item.Items
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.service.ShopChangeListener
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID

/**
 * Searching across all searchable shops. The searchable text of every trade (item names, lore, MMIDs, material
 * names) is built on first use and dropped when the shop changes.
 */
class SearchIndex(private val sis: Sis) : ShopChangeListener {
    private data class Text(val costs: String, val rewards: String)

    private val cache = HashMap<UUID, Text>()

    fun clear() = cache.clear()

    override fun added(shop: Shop) = forget(shop)
    override fun changed(shop: Shop, respawn: Boolean) = forget(shop)
    override fun removed(shop: Shop) = forget(shop)

    private fun forget(shop: Shop) = shop.trades.forEach { cache.remove(it.id) }

    private fun text(trade: Trade): Text = cache.getOrPut(trade.id) {
        Text(trade.costs.joinToString("\n", transform = ::entryText), trade.rewards.joinToString("\n", transform = ::entryText))
    }

    private fun entryText(entry: TradeEntry): String {
        val parts = ArrayList<String>()
        when (entry) {
            is TradeEntry.Item -> parts += entry.item.type.key.key
            is TradeEntry.Mythic -> parts += entry.id
            is TradeEntry.Money -> parts += "money"
            is TradeEntry.Command -> parts += entry.description
        }
        Items.template(entry)?.let { item ->
            parts += Messages.plain(item.effectiveName())
            item.lore()?.forEach { parts += Messages.plain(it) }
        }
        return parts.joinToString(" ").lowercase(Locale.ROOT)
    }

    fun search(viewer: Player, keyword: String, target: SearchTarget, held: ItemStack?): List<Pair<Shop, Trade>> {
        val key = keyword.lowercase(Locale.ROOT)
        val admin = viewer.hasPermission("sis.admin")
        val heldId = Items.mythicId(held)
        val result = ArrayList<Pair<Shop, Trade>>()
        for (shop in sis.shops.all) {
            if (!shop.searchable && !admin) continue
            if (!shop.canOpen(viewer)) continue
            val shopHit = key.isNotEmpty() && sis.shops.plainName(shop).lowercase(Locale.ROOT).contains(key)
            for (trade in shop.trades) {
                if (!sis.trades.isValid(trade) && !admin) continue
                val hit = when (target) {
                    SearchTarget.SHOPS -> shopHit
                    else -> {
                        val sides = when (target) {
                            SearchTarget.COSTS -> listOf(trade.costs to text(trade).costs)
                            SearchTarget.REWARDS -> listOf(trade.rewards to text(trade).rewards)
                            else -> listOf(trade.costs to text(trade).costs, trade.rewards to text(trade).rewards)
                        }
                        sides.any { (entries, text) ->
                            val textOk = key.isEmpty() || text.contains(key)
                            val itemOk = held == null || entries.any { e -> Items.matches(e, held) || (heldId != null && e is TradeEntry.Mythic && e.id == heldId) }
                            textOk && itemOk
                        }
                    }
                }
                if (hit) result += shop to trade
                if (result.size >= sis.config.maxSearchResults) return result
            }
        }
        return result
    }
}

object SearchMenus {
    lateinit var index: SearchIndex

    /**
     * Trades that have [entry]'s item among their costs ([findIn] = COSTS: what it is used for) or rewards
     * (REWARDS: where it can be obtained). Opened by clicking an item in a trade, as in 1.x.
     */
    fun byItem(sis: Sis, player: Player, entry: TradeEntry, findIn: SearchTarget) {
        val template = Items.template(entry) ?: return
        val mythicId = (entry as? TradeEntry.Mythic)?.id ?: Items.mythicId(template)
        val results = ArrayList<Pair<Shop, Trade>>()
        for (shop in sis.shops.all) {
            if (!shop.searchable && !player.hasPermission("sis.admin")) continue
            if (!shop.canOpen(player)) continue
            for (trade in shop.trades) {
                val side = if (findIn == SearchTarget.COSTS) trade.costs else trade.rewards
                val hit = side.any { e -> Items.matches(e, template) || (mythicId != null && e is TradeEntry.Mythic && e.id == mythicId) }
                if (hit) results += shop to trade
                if (results.size >= sis.config.maxSearchResults) break
            }
        }
        if (results.isEmpty()) {
            Messages.send(player, "search.no-results")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1f)
            return
        }
        Messages.send(player, if (findIn == SearchTarget.COSTS) "search.found-uses" else "search.found-sources",
            "count" to results.size, "item" to sis.icons.itemName(template))
        val parent = player.openInventory.topInventory.holder as? Menu
        TradeMenu(sis, player, null, results, adminView = player.hasPermission("sis.admin")).also { it.parent = parent }.open()
    }

    fun run(sis: Sis, player: Player, keyword: String, target: SearchTarget, held: ItemStack?) {
        if (keyword.isEmpty() && held == null) {
            Messages.send(player, "search.empty-query")
            return
        }
        val results = index.search(player, keyword, target, held)
        if (results.isEmpty()) {
            Messages.send(player, "search.no-results")
            return
        }
        Messages.send(player, "search.found", "count" to results.size)
        TradeMenu(sis, player, null, results, adminView = player.hasPermission("sis.admin")).open()
    }
}

/** Every shop (searchable ones for players). Click: open / see location; shift-click (admins): edit. */
class ShopListMenu(private val sis: Sis, viewer: Player) : Menu(viewer, 6) {
    private var page = 0
    override val pageButtons = 45 to 47
    private val perPage = 45

    private fun shops(): List<Shop> {
        val admin = viewer.hasPermission("sis.admin")
        return sis.shops.all.filter { (admin || it.searchable) && it.canOpen(viewer) }.sortedBy { sis.shops.plainName(it) }
    }

    override fun title(): Component = Messages.get("list.title")

    override fun render() {
        val list = shops()
        val pages = maxOf(1, (list.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)
        val admin = viewer.hasPermission("sis.admin")
        for (i in 0 until perPage) {
            val shop = list.getOrNull(page * perPage + i)
            if (shop == null) {
                slot(i, null)
                continue
            }
            val icon = shopIcon(shop)
                .named(sis.shops.displayName(shop))
                .loreLines(Messages.lines(
                    if (admin) "list.entry-lore-admin" else "list.entry-lore",
                    "location" to shop.location.describe(), "trades" to shop.trades.size, "id" to shop.id,
                    "searchable" to Messages.get(if (shop.searchable) "gui.answer-yes" else "gui.answer-no"),
                    "locked" to Messages.get(if (shop.locked) "gui.answer-yes" else "gui.answer-no"),
                ))
                .plainTooltip()
            slot(i, icon) { click ->
                when {
                    admin && click.shift -> EditorMenus.openShopEditor(sis, viewer, shop, this)
                    admin && click.right -> shop.location.toBukkit()?.let { viewer.teleportAsync(it) }
                    admin || sis.config.allowRemoteTrade -> if (!TradeMenu.refuseOpen(viewer, shop)) TradeMenu(sis, viewer, shop, adminView = admin).also { it.parent = this }.open()
                    else -> {
                        viewer.closeInventory()
                        Messages.send(viewer, "search.shop-location", "shop" to sis.shops.displayName(shop), "location" to shop.location.describe())
                    }
                }
            }
        }
        if (page > 0) slot(45, sis.icons.icon("previous", Messages.get("gui.previous-page"), Messages.lines("gui.page-hint"))) { page--; refresh() } else slot(45, sis.icons.filler())
        slot(46, sis.icons.icon("info", Messages.get("gui.page", "page" to page + 1, "pages" to pages)))
        if (page < pages - 1) slot(47, sis.icons.icon("next", Messages.get("gui.next-page"), Messages.lines("gui.page-hint"))) { page++; refresh() } else slot(47, sis.icons.filler())
        for (i in 48..52) slot(i, sis.icons.filler())
        slot(49, sis.icons.icon("search", Messages.get("gui.search"))) { sis.dialogs.search(viewer, this) }
        slot(53, sis.icons.icon("close", Messages.get("gui.close"))) { back() }
    }

    private fun shopIcon(shop: Shop): ItemStack {
        val first = shop.trades.firstOrNull()?.rewards?.firstOrNull()
        return (first?.let { sis.icons.entry(it) } ?: sis.icons.icon("open-shop")).also { it.amount = 1 }
    }
}
