package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trade pages cache their trades. Building a page must not depend on the trade list, because the editor
 * pages are numbered by editor page, not by trade page, and never read the page's trades.
 */
class ShopGuiPageCacheTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    private static List<ShopTrade> trades(int n) {
        List<ShopTrade> list = new ArrayList<>();
        for (int i = 0; i < n; i++)
            list.add(new ShopTrade(new ItemStack[]{new ItemStack(Material.DIAMOND, 1)},
                    new ItemStack[]{new ItemStack(Material.EMERALD, 1)}));
        return list;
    }

    @Test
    void editorPageDoesNotResolveTradePages() {
        Shop shop = ShopUtil.createNewShop(new Location(world, 0, 60, 0), "VILLAGER", null);
        shop.setTrades(trades(3));

        // page 99 is far past the single trade page; building the editor must not split the trade list
        assertDoesNotThrow(() -> new ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopEditorGui(shop, 99) {});
    }

    @Test
    void tradePageReturnsOnlyItsOwnTrades() {
        Shop shop = ShopUtil.createNewShop(new Location(world, 1, 60, 0), "VILLAGER", null);
        shop.setTrades(trades(shop.getShopType().getLimitSize() + 2));

        assertEquals(shop.getShopType().getLimitSize(), shop.getPage(1).getTrades().size());
        assertEquals(2, shop.getPage(2).getTrades().size());
    }

    @Test
    void getTradesRejectsPageZero() {
        Shop shop = ShopUtil.createNewShop(new Location(world, 2, 60, 0), "VILLAGER", null);
        shop.setTrades(trades(1));

        assertThrows(IndexOutOfBoundsException.class, () -> shop.getTrades(0));
    }
}
