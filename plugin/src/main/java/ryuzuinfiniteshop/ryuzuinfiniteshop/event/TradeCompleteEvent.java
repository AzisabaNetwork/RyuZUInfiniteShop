package ryuzuinfiniteshop.ryuzuinfiniteshop.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;

public class TradeCompleteEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final ShopTrade instance;
    private final Player player;
    private final ShopTrade.TradeResult tradeResult;

    public TradeCompleteEvent(ShopTrade instance, Player player, ShopTrade.TradeResult tradeResult) {
        this.instance = instance;
        this.player = player;
        this.tradeResult = tradeResult;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public ShopTrade getInstance() {
        return instance;
    }

    public Player getPlayer() {
        return player;
    }

    public ShopTrade.TradeResult getTradeResult() {
        return tradeResult;
    }
}
