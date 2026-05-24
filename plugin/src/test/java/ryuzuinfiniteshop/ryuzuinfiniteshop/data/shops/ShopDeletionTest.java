package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopDeletionTest {

    @BeforeEach
    void cleanShops() {
        ShopUtil.getShops().clear();
    }

    @Test
    @Order(1)
    void shopsMapInitiallyEmpty() {
        assertTrue(ShopUtil.getShops().isEmpty());
    }

    @Test
    @Order(2)
    void removeNonExistentShopDoesNotThrow() {
        assertDoesNotThrow(() -> ShopUtil.removeShop("nonexistent"));
    }

    @Test
    @Order(3)
    void addAndRemoveShopClearsEntry() {
        Shop mockShop = mock(Shop.class);
        when(mockShop.getID()).thenReturn("test_shop");
        ShopUtil.addShop("test_shop", mockShop);
        assertTrue(ShopUtil.getShops().containsKey("test_shop"));
        ShopUtil.removeShop("test_shop");
        assertFalse(ShopUtil.getShops().containsKey("test_shop"));
    }

    @Test
    @Order(4)
    void removeShopThenAddSameKeySucceeds() {
        Shop mockShop = mock(Shop.class);
        when(mockShop.getID()).thenReturn("test_shop");
        ShopUtil.addShop("test_shop", mockShop);
        ShopUtil.removeShop("test_shop");
        assertFalse(ShopUtil.getShops().containsKey("test_shop"));
        ShopUtil.addShop("test_shop", mockShop);
        assertTrue(ShopUtil.getShops().containsKey("test_shop"));
    }

}
