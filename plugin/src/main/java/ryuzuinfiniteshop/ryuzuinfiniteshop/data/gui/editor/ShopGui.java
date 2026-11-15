package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor;

import lombok.Getter;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;

import java.util.List;

@Getter
public abstract class ShopGui extends TradesGui {
    protected final Shop shop;
    /**
     * Page trades are fixed once built; {@link Shop#updateTradeContents()} recreates pages after any change.
     * Resolved lazily because the editor GUIs ({@link ShopEditorGui}, {@link ConfirmRemoveGui}) extend this
     * class but never read the page's trades, and their page number is an editor page, not a trade page.
     */
    private List<ShopTrade> pageTrades;

    public ShopGui(Shop shop, int page) {
        super(page);
        this.shop = shop;
    }

    @Override
    public List<ShopTrade> getTrades() {
        if (pageTrades == null) pageTrades = shop.getTrades(getPage());
        return pageTrades;
    }
}
