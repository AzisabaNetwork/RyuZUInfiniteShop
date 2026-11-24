package net.azisaba.sis.model

import org.bukkit.inventory.ItemStack
import java.util.UUID

/** One thing a trade takes from the player (cost) or gives to the player (reward). */
sealed interface TradeEntry {
    /** A plain item. [item] always has an amount of 1; [amount] is unbounded (256 diamonds is one entry). */
    data class Item(val item: ItemStack, val amount: Int) : TradeEntry {
        init {
            require(amount > 0) { "amount must be positive" }
        }
    }

    /** A MythicMobs item kept by its MMID, so the shop always hands out what MythicMobs currently generates. */
    data class Mythic(val id: String, val amount: Int) : TradeEntry {
        init {
            require(amount > 0) { "amount must be positive" }
        }
    }

    /** Vault money. */
    data class Money(val amount: Double) : TradeEntry {
        init {
            require(amount > 0) { "amount must be positive" }
        }
    }

    /**
     * A console command (rewards only). `<player>` and `<uuid>` are replaced. [display] is what the GUI shows;
     * [description] is shown as its lore.
     */
    data class Command(val command: String, val description: String, val display: ItemStack?) : TradeEntry
}

enum class LimitScope { PLAYER, GLOBAL }

enum class ResetPeriod { NEVER, DAILY, WEEKLY, MONTHLY }

data class TradeOptions(
    /** 0 = unlimited. */
    val limit: Int = 0,
    val limitScope: LimitScope = LimitScope.PLAYER,
    val reset: ResetPeriod = ResetPeriod.NEVER,
    /** Success chance in percent (0..100; 0 = never succeeds, as in 1.x). Costs are always taken; rewards are given on success. */
    val chance: Int = 100,
    val hideChance: Boolean = false,
    /** Permission the player needs for this trade, or null. */
    val permission: String? = null,
) {
    init {
        require(limit >= 0) { "limit must not be negative" }
        require(chance in 0..100) { "chance must be 0..100" }
    }

    val hasLimit: Boolean get() = limit > 0
}

data class Trade(
    val id: UUID = UUID.randomUUID(),
    val costs: List<TradeEntry>,
    val rewards: List<TradeEntry>,
    val options: TradeOptions = TradeOptions(),
) {
    val isEmpty: Boolean get() = costs.isEmpty() && rewards.isEmpty()

    fun money(of: List<TradeEntry>): Double = of.filterIsInstance<TradeEntry.Money>().sumOf { it.amount }
}
