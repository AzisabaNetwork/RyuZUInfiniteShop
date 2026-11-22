package net.azisaba.sis.service

import net.azisaba.sis.api.event.ShopPreTradeEvent
import net.azisaba.sis.api.event.ShopTradeEvent
import net.azisaba.sis.item.Items
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ryuzuinfiniteshop.ryuzuinfiniteshop.event.TradeCompleteEvent
import java.util.concurrent.ThreadLocalRandom

enum class TradeStatus {
    READY, NOT_ENOUGH_ITEMS, NOT_ENOUGH_MONEY, LIMIT_REACHED, INVENTORY_FULL, LOCKED, NO_PERMISSION, INVALID, CANCELLED;

    val ok: Boolean get() = this == READY
}

/** How many times (at most [times]) a trade can be done now; [status] says why not more when [times] is 0. */
data class Evaluation(val times: Int, val status: TradeStatus)

data class TradeOutcome(val requested: Int, val times: Int, val successes: Int, val status: TradeStatus)

/**
 * Checks and performs trades. A trade is done N times at once: everything is checked on a copy of the inventory
 * first (costs taken, rewards added), and only applied when all of it fits.
 *
 * Costs are taken from loose items in the 36 main slots first, then from containers ([StoredItems]: Storage Boxes)
 * in those slots or the off hand. Rewards go into a container already holding that item, otherwise into the main
 * slots. Both uses of containers can be turned off per player ([PlayerSettings]).
 */
class TradeService(
    private val counter: TradeCounter,
    private val stored: StoredItems = StoredItems.NONE,
    private val settings: (java.util.UUID) -> PlayerSettings = { PlayerSettings() },
    private val onTraded: (Player, Shop, Trade, Int, Int) -> Unit,
) {
    /** Upper bound for "buy as many as possible". */
    val maxPerClick = 64 * 36

    /** A container found in the inventory. [slot] is a main-slot index, or [OFF_HAND]. */
    private class Box(val slot: Int, val item: ItemStack, val stored: StoredItems.Stored)

    /**
     * What the player holds: the main slots and the containers among them / in the off hand, and whether costs may
     * come out of the containers ([pay]) and rewards go into them ([store]) (the player's settings).
     */
    private class Holdings(val contents: Array<ItemStack?>, val boxes: List<Box>, val pay: Boolean, val store: Boolean)

    /** The holdings after a simulated trade. */
    private class Result(val contents: Array<ItemStack?>, val boxAmounts: LongArray)

    private fun itemCosts(trade: Trade) = trade.costs.filter { it is TradeEntry.Item || it is TradeEntry.Mythic }
    private fun itemRewards(trade: Trade) = trade.rewards.filter { it is TradeEntry.Item || it is TradeEntry.Mythic }

    private fun amountOf(entry: TradeEntry): Long = when (entry) {
        is TradeEntry.Item -> entry.amount.toLong()
        is TradeEntry.Mythic -> entry.amount.toLong()
        else -> 0
    }

    fun isValid(trade: Trade): Boolean {
        if (trade.rewards.isEmpty() && trade.costs.isEmpty()) return false
        val all = trade.costs + trade.rewards
        if (all.any { !Items.isValid(it) }) return false
        if (all.any { it is TradeEntry.Money } && !Economy.available) return false
        return true
    }

    private fun holdings(player: Player): Holdings {
        val inventory = player.inventory
        val contents = inventory.storageContents
        val boxes = ArrayList<Box>()
        fun scan(slot: Int, item: ItemStack?) {
            if (item == null || item.type.isAir) return
            val content = runCatching { stored.read(item) }.getOrNull() ?: return
            boxes += Box(slot, item, content)
        }
        contents.forEachIndexed { slot, item -> scan(slot, item) }
        scan(OFF_HAND, inventory.itemInOffHand)
        val s = settings(player.uniqueId)
        return Holdings(contents, boxes, s.payFromBoxes, s.rewardsIntoBoxes)
    }

    /** Matches loose items only: a container is never taken as a cost itself. */
    private fun looseMatcher(h: Holdings, cost: TradeEntry): (ItemStack) -> Boolean {
        if (h.boxes.isEmpty()) return { Items.matches(cost, it) }
        return { stored.read(it) == null && Items.matches(cost, it) }
    }

    fun evaluate(player: Player, shop: Shop, trade: Trade, wanted: Int, bypassLock: Boolean = false): Evaluation {
        if (!isValid(trade)) return Evaluation(0, TradeStatus.INVALID)
        if (!bypassLock && !shop.canOpen(player)) return Evaluation(0, TradeStatus.LOCKED)
        trade.options.permission?.let { if (!player.hasPermission(it)) return Evaluation(0, TradeStatus.NO_PERMISSION) }

        var bound = wanted.coerceAtLeast(0)
        val h = holdings(player)

        for (cost in itemCosts(trade)) {
            val loose = InventorySim.count(h.contents, looseMatcher(h, cost))
            val inBoxes = if (!h.pay) 0L else h.boxes.filter { Items.matches(cost, it.stored.content) }.sumOf { it.stored.amount }
            val possible = ((loose + inBoxes) / amountOf(cost)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (possible == 0) return Evaluation(0, TradeStatus.NOT_ENOUGH_ITEMS)
            bound = minOf(bound, possible)
        }
        val moneyCost = trade.money(trade.costs)
        if (moneyCost > 0) {
            val possible = Math.floor(Economy.balance(player) / moneyCost).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
            if (possible == 0) return Evaluation(0, TradeStatus.NOT_ENOUGH_MONEY)
            bound = minOf(bound, possible)
        }
        val remaining = counter.remaining(trade, player.uniqueId)
        if (remaining == 0) return Evaluation(0, TradeStatus.LIMIT_REACHED)
        bound = minOf(bound, remaining)
        if (bound == 0) return Evaluation(0, TradeStatus.READY)

        // Several entries may match the same stacks, and rewards need room: find the largest N that really fits.
        if (simulate(h, trade, bound).second == null) return Evaluation(bound, TradeStatus.READY)
        var low = 0
        var high = bound
        var lowFailure = TradeStatus.INVENTORY_FULL
        while (low < high) {
            val mid = (low + high + 1) / 2
            val failure = simulate(h, trade, mid).second
            if (failure == null) low = mid else {
                high = mid - 1
                lowFailure = failure
            }
        }
        return Evaluation(low, if (low == 0) lowFailure else TradeStatus.READY)
    }

    /**
     * Does the trade [times] times on a copy of [h] ([successes] of them hand out rewards).
     * Returns the result, or the reason it does not work.
     */
    private fun simulate(h: Holdings, trade: Trade, times: Int, successes: Int = times): Pair<Result?, TradeStatus?> {
        val copy = InventorySim.copy(h.contents)
        val amounts = LongArray(h.boxes.size) { h.boxes[it].stored.amount }
        for (cost in itemCosts(trade)) {
            var need = amountOf(cost) * times
            val matcher = looseMatcher(h, cost)
            val loose = minOf(need, InventorySim.count(copy, matcher))
            InventorySim.remove(copy, loose, matcher)
            need -= loose
            for (i in h.boxes.indices) {
                if (need <= 0 || !h.pay) break
                if (amounts[i] <= 0 || !Items.matches(cost, h.boxes[i].stored.content)) continue
                val taken = minOf(need, amounts[i])
                amounts[i] -= taken
                need -= taken
            }
            if (need > 0) return null to TradeStatus.NOT_ENOUGH_ITEMS
        }
        for (reward in itemRewards(trade)) {
            val template = Items.template(reward) ?: return null to TradeStatus.INVALID
            val total = amountOf(reward) * successes
            // Into a Storage Box that already holds this item: no inventory space needed.
            val box = if (h.store) h.boxes.indices.firstOrNull { Items.matches(reward, h.boxes[it].stored.content) } else null
            if (box != null) {
                amounts[box] += total
                continue
            }
            for (stack in Items.stacks(template, total)) {
                if (InventorySim.add(copy, stack) > 0) return null to TradeStatus.INVENTORY_FULL
            }
        }
        return Result(copy, amounts) to null
    }

    fun execute(player: Player, shop: Shop, trade: Trade, wanted: Int, bypassLock: Boolean = false): TradeOutcome {
        val evaluation = evaluate(player, shop, trade, wanted, bypassLock)
        if (evaluation.times == 0) return TradeOutcome(wanted, 0, 0, evaluation.status)
        val times = evaluation.times
        if (!ShopPreTradeEvent(player, shop, trade, times).callEvent()) return TradeOutcome(wanted, 0, 0, TradeStatus.CANCELLED)

        val chance = trade.options.chance
        val successes = if (chance >= 100) times else (1..times).count { ThreadLocalRandom.current().nextInt(100) < chance }

        val inventory = player.inventory
        val h = holdings(player)
        val (after, failure) = simulate(h, trade, times, successes)
        if (after == null) return TradeOutcome(wanted, 0, 0, failure ?: TradeStatus.INVENTORY_FULL)
        val moneyCost = trade.money(trade.costs) * times
        if (moneyCost > 0 && !Economy.withdraw(player, moneyCost)) return TradeOutcome(wanted, 0, 0, TradeStatus.NOT_ENOUGH_MONEY)
        h.boxes.forEachIndexed { i, box ->
            if (after.boxAmounts[i] == box.stored.amount) return@forEachIndexed
            val updated = stored.withAmount(box.item, after.boxAmounts[i])
            if (box.slot == OFF_HAND) inventory.setItemInOffHand(updated) else after.contents[box.slot] = updated
        }
        inventory.storageContents = after.contents

        val moneyReward = trade.money(trade.rewards) * successes
        if (moneyReward > 0) Economy.deposit(player, moneyReward)
        repeat(successes) {
            trade.rewards.filterIsInstance<TradeEntry.Command>().forEach { reward ->
                val command = reward.command.removePrefix("/")
                    .replace("<player>", player.name).replace("<uuid>", player.uniqueId.toString())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            }
        }
        counter.add(trade, player.uniqueId, times)

        ShopTradeEvent(player, shop, trade, times, successes).callEvent()
        if (TradeCompleteEvent.getHandlerList().registeredListeners.isNotEmpty()) {
            fun stacksOf(entries: List<TradeEntry>) = entries.flatMap { e -> Items.template(e)?.let { Items.stacks(it, amountOf(e)) }.orEmpty() }.toTypedArray()
            val give = stacksOf(itemRewards(trade))
            val take = stacksOf(itemCosts(trade))
            repeat(times) { i -> TradeCompleteEvent(player, give, take, i < successes).callEvent() }
        }
        onTraded(player, shop, trade, times, successes)
        return TradeOutcome(wanted, times, successes, TradeStatus.READY)
    }

    private companion object {
        const val OFF_HAND = -1
    }
}
