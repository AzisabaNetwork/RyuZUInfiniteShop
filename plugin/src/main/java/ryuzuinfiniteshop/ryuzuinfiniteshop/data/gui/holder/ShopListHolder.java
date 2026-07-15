package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder;

import lombok.Getter;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.ShopListGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;

import java.util.LinkedHashMap;

@Getter
public class ShopListHolder extends PageableHolder {
    protected final LinkedHashMap<String, Shop> shops;

    public ShopListHolder(ShopMode mode, ShopListGui gui, LinkedHashMap<String, Shop> shops) {
        super(mode, gui);
        this.shops = shops;
    }

    public ShopListHolder(ShopMode mode, ShopListGui gui, LinkedHashMap<String, Shop> shops, ModeHolder before) {
        super(mode, gui, before);
        this.shops = shops;
    }

    @Override
    public int getMaxPage() {
        return Math.max(1, (int) Math.ceil((double) shops.size() / PageNavigationUtil.LIST_PAGE_SIZE));
    }

    @Override
    public ShopListGui getGui() {
        return (ShopListGui) super.getGui();
    }
}
