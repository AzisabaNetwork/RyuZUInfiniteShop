package ryuzuinfiniteshop.ryuzuinfiniteshop.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * The 1.x event, kept under its old name for plugins that listen to it by class name (LifeQuestCore).
 * Fired once per attempt, like 1.x. New code should listen to {@link net.azisaba.sis.api.event.ShopTradeEvent}.
 *
 * @deprecated use {@link net.azisaba.sis.api.event.ShopTradeEvent}
 */
@Deprecated
public class TradeCompleteEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Instance instance;
    private final Player player;
    private final Result tradeResult;

    public TradeCompleteEvent(Player player, ItemStack[] giveItems, ItemStack[] takeItems, boolean success) {
        this.instance = new Instance(giveItems, takeItems);
        this.player = player;
        this.tradeResult = success ? Result.Success : Result.Fail;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /** Has {@code getGiveItems()} (what the player received) and {@code getTakeItems()} (what they paid), like 1.x. */
    public Instance getInstance() {
        return instance;
    }

    public Player getPlayer() {
        return player;
    }

    public Result getTradeResult() {
        return tradeResult;
    }

    /** Same names as the 1.x {@code ShopTrade.TradeResult} values this event used. */
    public enum Result {Success, Fail}

    public static final class Instance {
        private final ItemStack[] give;
        private final ItemStack[] take;

        Instance(ItemStack[] give, ItemStack[] take) {
            this.give = give;
            this.take = take;
        }

        public ItemStack[] getGiveItems() {
            return give.clone();
        }

        public ItemStack[] getTakeItems() {
            return take.clone();
        }
    }
}
