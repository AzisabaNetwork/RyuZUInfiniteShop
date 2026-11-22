package net.azisaba.sis.api.event

import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.Trade
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/** Before a trade is done [times] times. Cancel to stop it; nothing has been taken yet. */
class ShopPreTradeEvent(player: Player, val shop: Shop, val trade: Trade, val times: Int) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

/**
 * After a trade was done. [times] attempts were paid for; [successes] of them gave their rewards
 * (fewer than [times] only for trades with a success chance below 100 %).
 */
class ShopTradeEvent(player: Player, val shop: Shop, val trade: Trade, val times: Int, val successes: Int) : PlayerEvent(player) {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
