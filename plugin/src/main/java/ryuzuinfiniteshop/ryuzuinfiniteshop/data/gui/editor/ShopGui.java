package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor;

import lombok.Getter;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;

import java.util.List;

@Getter
public abstract class ShopGui extends TradesGui {
    protected final Shop shop;
    /** Page trades are fixed when the page is built; {@link Shop#updateTradeContents()} recreates pages after any change. */
    private final List<ShopTrade> pageTrades;

    public ShopGui(Shop shop, int page) {
        super(page);
        this.shop = shop;
        this.pageTrades = shop.getTrades(page);
    }

    @Override
    public List<ShopTrade> getTrades() {
        return pageTrades;
    }
}
