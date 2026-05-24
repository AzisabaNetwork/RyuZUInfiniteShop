package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui;

import org.junit.jupiter.api.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.ShopListGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class ShopListGuiTest {

    private LinkedHashMap<String, Shop> shops;

    @BeforeEach
    void setUp() {
        shops = new LinkedHashMap<>();
    }

    @Test
    void shopListGuiCorrectPage() {
        ShopListGui gui = new ShopListGui(5, shops);
        assertEquals(5, gui.getPage());
    }

    @Test
    void shopListGuiPageOneByDefault() {
        ShopListGui gui = new ShopListGui(1, shops);
        assertEquals(1, gui.getPage());
    }

    @Test
    void shopListGuiPageZero() {
        ShopListGui gui = new ShopListGui(0, shops);
        assertEquals(0, gui.getPage());
    }
}
