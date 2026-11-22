package net.azisaba.sis.gui

import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.dialog.SearchTarget
import net.azisaba.sis.model.ResetPeriod
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.service.TradeOutcome
import net.azisaba.sis.service.TradeStatus
import net.kyori.adventure.text.Component
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import java.time.format.DateTimeFormatter

/**
 * The villager-style trade screen.
 *
 * ```
 *  col: 0     1   2      3 | 4  5  6   7   8
 *  row0 [cost][→][reward] |  | c  c  c   .   r
 *  row1  one trade per row|  | c  c  c   .   r     right side: every cost and
 *  row2                   |  | c  c  c   →   r     reward of the selected trade
 *  row3                   |  | c  c  c   .   r
 *  row4                   |  | c  c  c   .   r
 *  row5 [<] [page] [>] [edit/open] [search] [x1] [x8] [x64] [max]
 * ```
 * Left side: the list (5 per page; several costs/rewards show as one bundle with a preview). Click a row to select it, shift-click to buy once right away.
 * Right side: the selected trade in full; there is no limit on entries (overflow is previewed in a bundle).
 *
 * With [fixedShop] it shows one shop; without, it shows search results from many shops.
 */
class TradeMenu(
    private val sis: Sis,
    viewer: Player,
    private val fixedShop: Shop?,
    private val results: List<Pair<Shop, Trade>> = emptyList(),
    private val adminView: Boolean = false,
    /** Trades work even when the shop is locked (opened by /sis open, or by someone who may pass locks). */
    private val unlocked: Boolean = adminView,
) : Menu(viewer, 6) {
    private var page = 0
    override val pageButtons = 45 to 47
    private var selected: Pair<Shop, Trade>? = null

    private val entries: List<Pair<Shop, Trade>>
        get() = fixedShop?.let { shop -> shop.trades.map { shop to it } } ?: results

    private val pages: Int get() = maxOf(1, (entries.size + PER_PAGE - 1) / PER_PAGE)

    override fun title(): Component {
        val template = if (fixedShop != null) sis.config.guiTitle else sis.config.searchTitle
        val shopName = fixedShop?.let { sis.shops.displayName(it) } ?: Component.empty()
        return Messages.parseTemplate(template, "shop" to shopName, "page" to (page + 1).toString(), "pages" to pages.toString())
    }

    override fun render() {
        val list = entries
        page = page.coerceIn(0, pages - 1)
        selected = selected?.takeIf { sel -> list.any { it.second.id == sel.second.id } }?.let { sel -> list.first { it.second.id == sel.second.id } }
        if (selected == null) selected = list.firstOrNull()

        val icons = sis.icons
        for (row in 0 until PER_PAGE) {
            val index = page * PER_PAGE + row
            val pair = list.getOrNull(index)
            if (pair == null) {
                for (c in 0..2) slot(row * 9 + c, icons.filler())
                slot(row * 9 + 3, icons.filler("divider"))
                continue
            }
            val (shop, trade) = pair
            val status = sis.trades.evaluate(viewer, shop, trade, 1, bypassLock = unlocked).status
            val isSelected = selected?.second?.id == trade.id
            val summary = summaryLore(shop, trade, status)
            val onClick: (Click) -> Unit = { click ->
                selected = pair
                val times = if (tradeFromList) bulkTimes(click, leftTrades = false) else null
                if (times != null && canTrade(shop)) buy(pair, times) else {
                    viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.4f)
                    refresh()
                }
            }
            slot(row * 9, previewSlot(trade.costs, summary), onClick)
            slot(row * 9 + 1, statusIcon(status, isSelected, summary), onClick)
            slot(row * 9 + 2, previewSlot(trade.rewards, summary), onClick)
            val marker = if (isSelected) icons.icon("selected", Messages.get("trade.selected"), Messages.lines("trade.selected-lore") + listHints(shop)) else icons.filler("divider")
            slot(row * 9 + 3, marker, onClick)
        }
        renderDetail()
        renderBottom()
    }

    /** A list row cell: the only entry, or a bundle previewing all of them. */
    private fun previewSlot(items: List<TradeEntry>, lore: List<Component>): ItemStack {
        val icons = sis.icons
        if (items.isEmpty()) return icons.filler("empty-slot")
        val item = if (items.size == 1) icons.entry(items[0]) else icons.bundle(items, Messages.get("trade.more", "count" to items.size))
        return item.addLore(lore)
    }

    private fun statusIcon(status: TradeStatus, selected: Boolean, lore: List<Component>): ItemStack {
        val name = when (status) {
            TradeStatus.READY -> "status-ready"
            TradeStatus.LIMIT_REACHED -> "status-limited"
            TradeStatus.LOCKED -> "status-locked"
            else -> "status-unavailable"
        }
        val icon = sis.icons.icon(name, Messages.get("status.${status.name.lowercase()}"), lore)
        if (selected) icon.glint().addLore(listOf(Messages.get("trade.selected")))
        return icon
    }

    private fun summaryLore(shop: Shop, trade: Trade, status: TradeStatus): List<Component> {
        val lines = ArrayList<Component>()
        lines += Messages.get("trade.costs-header")
        trade.costs.forEach { lines += Messages.get("trade.bullet", "line" to sis.icons.describe(it)) }
        if (trade.costs.isEmpty()) lines += Messages.get("trade.free")
        lines += Messages.get("trade.rewards-header")
        trade.rewards.forEach { lines += Messages.get("trade.bullet", "line" to sis.icons.describe(it)) }
        lines += optionLines(trade)
        if (fixedShop == null) lines += Messages.get("trade.from-shop", "shop" to sis.shops.displayName(shop), "location" to shop.location.describe())
        lines += Component.empty()
        lines += Messages.get("status.${status.name.lowercase()}")
        lines += listHints(shop)
        return lines
    }

    private fun optionLines(trade: Trade): List<Component> {
        val o = trade.options
        val lines = ArrayList<Component>()
        if (o.hasLimit) {
            val used = sis.counter.count(trade, viewer.uniqueId)
            val key = "trade.limit-${o.limitScope.name.lowercase()}"
            lines += Messages.get(key, "left" to (o.limit - used).coerceAtLeast(0), "max" to o.limit)
            if (o.reset != ResetPeriod.NEVER) {
                val next = sis.clock.nextReset(o.reset)
                lines += Messages.get("trade.reset-${o.reset.name.lowercase()}", "time" to (next?.format(RESET_FORMAT) ?: ""))
            }
        }
        if (o.chance < 100) lines += Messages.get("trade.chance", "chance" to if (o.hideChance && !adminView) "?" else o.chance.toString())
        return lines
    }

    private fun renderDetail() {
        val icons = sis.icons
        val pair = selected
        val costSlots = (0..4).flatMap { r -> (4..6).map { c -> r * 9 + c } }
        val rewardSlots = (0..4).map { r -> r * 9 + 8 }
        for (r in 0..4) slot(r * 9 + 7, icons.filler("divider"))
        if (pair == null) {
            (costSlots + rewardSlots).forEach { slot(it, icons.filler("empty-slot")) }
            return
        }
        val (shop, trade) = pair
        fill(costSlots, trade.costs, cost = true)
        fill(rewardSlots, trade.rewards, cost = false)
        val status = sis.trades.evaluate(viewer, shop, trade, 1, bypassLock = unlocked).status
        // Like the villager's result slot: clicking trades once (or the villager says no).
        val tradable = canTrade(shop)
        val lore = optionLines(trade) + Messages.lines(if (tradable) "trade.arrow-hint" else "trade.list-hint-view")
        slot(2 * 9 + 7, icons.icon("arrow", Messages.get("status.${status.name.lowercase()}"), lore)) { click ->
            val times = bulkTimes(click, leftTrades = true) ?: return@slot
            if (tradable) buy(pair, times) else fail()
        }
    }

    /**
     * One side of the selected trade. Clicking an item searches every shop for it, like 1.x:
     * a cost → where it can be obtained (trades giving it), a reward → what it is used for (trades taking it).
     * Admins copy the item with a middle-click.
     */
    private fun fill(slots: List<Int>, items: List<TradeEntry>, cost: Boolean) {
        val icons = sis.icons
        val canSearch = viewer.hasPermission("sis.search")
        val admin = viewer.hasPermission("sis.admin")
        slots.forEachIndexed { i, s ->
            if (i >= items.size) return@forEachIndexed slot(s, icons.filler("empty-slot"))
            if (i == slots.lastIndex && items.size > slots.size) {
                return@forEachIndexed slot(s, icons.bundle(items.drop(i), Messages.get("trade.more", "count" to items.size - i)))
            }
            val entry = items[i]
            val searchable = canSearch && (entry is TradeEntry.Item || entry is TradeEntry.Mythic)
            val item = icons.entry(entry)
            if (searchable) item.addLore(Messages.lines(if (cost) "trade.search-cost-hint" else "trade.search-reward-hint"))
            slot(s, item) { click ->
                when {
                    click.type == ClickType.MIDDLE && admin -> ItemCopy.toCursor(viewer, click.event, entry)
                    (click.left || click.right) && searchable -> SearchMenus.byItem(sis, viewer, entry, findIn = if (cost) SearchTarget.REWARDS else SearchTarget.COSTS)
                }
            }
        }
    }

    private fun renderBottom() {
        val icons = sis.icons
        val base = 45
        val pageLore = listOf(Messages.get("gui.page", "page" to page + 1, "pages" to pages)) + Messages.lines("gui.page-hint")
        if (page > 0) slot(base, icons.icon("previous", Messages.get("gui.previous-page"), pageLore)) { page--; click(); refresh() } else slot(base, icons.filler())
        slot(base + 1, icons.icon("player-settings", Messages.get("gui.settings"), Messages.lines("gui.settings-lore"))) {
            sis.dialogs.playerSettings(viewer, this)
        }
        if (page < pages - 1) slot(base + 2, icons.icon("next", Messages.get("gui.next-page"), pageLore)) { page++; click(); refresh() } else slot(base + 2, icons.filler())

        val pair = selected
        // Slot 3: back to search / open the shop of a search result / edit (admins)
        when {
            fixedShop == null && pair != null -> slot(base + 3, icons.icon("open-shop", Messages.get("search.open-shop"),
                Messages.lines("search.open-shop-lore", "shop" to sis.shops.displayName(pair.first), "location" to pair.first.location.describe()))) {
                openShopOf(pair.first)
            }
            fixedShop != null && viewer.hasPermission("sis.admin") -> slot(base + 3, icons.icon("edit", Messages.get("gui.edit-shop"))) {
                EditorMenus.openShopEditor(sis, viewer, fixedShop)
            }
            else -> slot(base + 3, icons.filler())
        }
        slot(base + 4, icons.icon("search", Messages.get("gui.search"), Messages.lines("gui.search-lore"))) {
            sis.dialogs.search(viewer)
        }
        if (pair == null || !canTrade(pair.first)) {
            for (i in 5..8) slot(base + i, icons.filler())
            return
        }
        val amounts = listOf("buy-1" to 1, "buy-8" to 8, "buy-64" to 64, "buy-max" to sis.trades.maxPerClick)
        amounts.forEachIndexed { i, (icon, times) ->
            val label = if (icon == "buy-max") Messages.get("gui.buy-max") else Messages.get("gui.buy", "times" to times)
            slot(base + 5 + i, icons.icon(icon, label)) { buy(pair, times) }
        }
    }

    /**
     * How many times a click trades, with the keys of 1.x: shift = 8, wheel = 64, Q (drop) = as many as possible.
     * A plain left click trades once on the result arrow ([leftTrades]); in the list it selects, and right-click
     * trades once instead. Null = not a trading click.
     */
    private fun bulkTimes(click: Click, leftTrades: Boolean): Int? = when (click.type) {
        ClickType.DROP, ClickType.CONTROL_DROP -> sis.trades.maxPerClick
        ClickType.MIDDLE -> 64
        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> 8
        ClickType.RIGHT -> 1
        ClickType.LEFT -> if (leftTrades) 1 else null
        else -> null
    }

    /** How the list rows react to clicks (with the trading keys when the player's setting allows them). */
    private fun listHints(shop: Shop): List<Component> =
        Messages.lines(if (canTrade(shop) && tradeFromList) "trade.list-hint" else "trade.list-hint-view")

    /** The player's setting: trading keys also work on the list. */
    private val tradeFromList: Boolean get() = sis.settings[viewer.uniqueId].tradeFromList

    private fun canTrade(shop: Shop): Boolean = fixedShop != null || sis.config.allowRemoteTrade || viewer.hasPermission("sis.admin")

    private fun buy(pair: Pair<Shop, Trade>, times: Int) {
        val (shop, trade) = pair
        val outcome = sis.trades.execute(viewer, shop, trade, times, bypassLock = unlocked)
        report(outcome)
        refresh()
    }

    private fun report(outcome: TradeOutcome) {
        if (outcome.times == 0) {
            if (outcome.status != TradeStatus.CANCELLED) Messages.send(viewer, "result.${outcome.status.name.lowercase()}")
            fail()
            return
        }
        val failed = outcome.times - outcome.successes
        when {
            failed > 0 && outcome.successes == 0 -> {
                Messages.send(viewer, "result.failed-chance", "times" to outcome.times)
                viewer.playSound(viewer.location, Sound.ENTITY_ITEM_BREAK, 0.7f, 1f)
            }
            failed > 0 -> {
                Messages.send(viewer, "result.partly-failed", "times" to outcome.times, "successes" to outcome.successes)
                viewer.playSound(viewer.location, Sound.ENTITY_VILLAGER_YES, 0.7f, 1f)
            }
            else -> {
                if (outcome.times < outcome.requested && outcome.requested != sis.trades.maxPerClick) {
                    Messages.send(viewer, "result.partial", "times" to outcome.times, "requested" to outcome.requested)
                } else if (outcome.times > 1) {
                    Messages.send(viewer, "result.success-many", "times" to outcome.times)
                }
                viewer.playSound(viewer.location, Sound.ENTITY_VILLAGER_YES, 0.7f, 1f)
            }
        }
    }

    private fun openShopOf(shop: Shop) {
        if (sis.config.allowRemoteTrade || viewer.hasPermission("sis.admin")) {
            if (TradeMenu.refuseOpen(viewer, shop)) return
            TradeMenu(sis, viewer, shop, adminView = viewer.hasPermission("sis.admin")).also { it.parent = this }.open()
        } else {
            viewer.closeInventory()
            Messages.send(viewer, "search.shop-location", "shop" to sis.shops.displayName(shop), "location" to shop.location.describe())
        }
    }

    private fun click() = viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
    private fun fail() = viewer.playSound(viewer.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1f)

    companion object {
        /**
         * Whether [player] must not open the trade screen of [shop]: it is locked for them, or it has no trades.
         * Then it tells them why (with the villager's "no"). Admins are told how to reach the editor.
         * [bypassLock]: opened for the player by `/sis open` (staff, command blocks), which 1.x allowed too.
         */
        fun refuseOpen(player: Player, shop: Shop, bypassLock: Boolean = false): Boolean {
            if (!bypassLock && !shop.canOpen(player)) {
                Messages.send(player, "shop.locked")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1f)
                return true
            }
            if (shop.trades.isNotEmpty()) return false
            Messages.send(player, "shop.no-trades")
            if (player.hasPermission("sis.admin")) Messages.send(player, "shop.no-trades-admin", "id" to shop.id)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1f)
            return true
        }

        const val PER_PAGE = 5
        private val RESET_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm")
    }
}
