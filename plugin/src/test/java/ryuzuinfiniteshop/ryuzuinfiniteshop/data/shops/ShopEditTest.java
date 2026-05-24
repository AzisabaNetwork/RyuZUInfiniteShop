package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopEditTest {

    @BeforeEach
    void cleanShops() {
        ShopUtil.getShops().clear();
    }

    @Test
    @Order(1)
    void getShopByIdAfterAdd() {
        Shop mockShop = mock(Shop.class);
        when(mockShop.getID()).thenReturn("test_shop");
        ShopUtil.addShop("test_shop", mockShop);
        assertSame(mockShop, ShopUtil.getShop("test_shop"));
    }

    @Test
    @Order(2)
    void getShopByIdReturnsNullForMissing() {
        assertNull(ShopUtil.getShop("nonexistent"));
    }

    @Test
    @Order(3)
    void shopsMapIsSharedInstance() {
        Shop mockShop = mock(Shop.class);
        when(mockShop.getID()).thenReturn("test_shop");
        ShopUtil.addShop("test_shop", mockShop);
        assertTrue(ShopUtil.getShops().containsKey("test_shop"));
    }

    @Test
    @Order(4)
    void clearShopsMapEmptiesAll() {
        Shop mock1 = mock(Shop.class);
        Shop mock2 = mock(Shop.class);
        ShopUtil.addShop("shop1", mock1);
        ShopUtil.addShop("shop2", mock2);
        assertEquals(2, ShopUtil.getShops().size());
        ShopUtil.getShops().clear();
        assertTrue(ShopUtil.getShops().isEmpty());
    }

    @Test
    @Order(5)
    void addShopOverwritesExistingKey() {
        Shop original = mock(Shop.class);
        Shop replacement = mock(Shop.class);
        ShopUtil.addShop("test_shop", original);
        ShopUtil.addShop("test_shop", replacement);
        assertSame(replacement, ShopUtil.getShop("test_shop"));
    }
}
