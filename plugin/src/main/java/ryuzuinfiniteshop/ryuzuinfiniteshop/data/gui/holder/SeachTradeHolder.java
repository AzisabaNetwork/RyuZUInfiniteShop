package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder;

import lombok.Getter;
import org.bukkit.entity.Player;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.SearchTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;

import java.util.LinkedHashMap;

@Getter
public class SeachTradeHolder extends PageableHolder {
    protected final LinkedHashMap<ShopTrade, Shop> trades;
    protected final Player player;

    public SeachTradeHolder(ShopMode mode, SearchTradeGui gui, Player player, LinkedHashMap<ShopTrade, Shop> trades) {
        super(mode, gui);
        this.player = player;
        this.trades = trades;
    }

    public SeachTradeHolder(ShopMode mode, SearchTradeGui gui, Player player, LinkedHashMap<ShopTrade, Shop> trades, ModeHolder before) {
        super(mode, gui, before);
        this.player = player;
        this.trades = trades;
    }

    public int getMaxPage() {
        return Math.max(1, (int) Math.ceil((double) trades.size() / PageNavigationUtil.SEARCH_PAGE_SIZE));
    }

    @Override
    public SearchTradeGui getGui() {
        return (SearchTradeGui) super.getGui();
    }
}
