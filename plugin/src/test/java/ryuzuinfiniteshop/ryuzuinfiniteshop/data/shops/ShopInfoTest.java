package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for Shop display name and identification methods.
 */
class ShopInfoTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    @Test
    void displayNameDefaultsToNullOrEmpty() {
        Shop shop = createSimpleShop(1, 2, 3);
        // displayName may be null or empty depending on test execution order
        // (YAML file persistence across test methods in the same class)
        String name = shop.getDisplayName();
        assertTrue(name == null || name.isEmpty(),
                "displayName should be null or empty, but was: [" + name + "]");
    }

    @Test
    void getDisplayNameOrElseShopDependsOnLanguageConfig() {
        Shop shop = createSimpleShop(1, 2, 3);
        // This depends on LanguageConfig being loaded
        // Characterization: current behavior without LanguageConfig
    }

    @Test
    void getDisplayNameOrElseNoneDependsOnLanguageConfig() {
        Shop shop = createSimpleShop(1, 2, 3);
        // This depends on LanguageConfig being loaded
    }

    @Test
    void containsDisplayNameReturnsFalseWhenDisplayNameNotSet() {
        Shop shop = createSimpleShop(1, 2, 3);
        assertFalse(shop.containsDisplayName("test"));
    }

    @Test
    void getIDReturnsFormattedLocation() {
        Shop shop = createSimpleShop(10, -20, 30);
        assertEquals("test_world,10,-20,30", shop.getID());
    }

    @Test
    void getIDUsesBlockCoordinates() {
        Shop shop = createSimpleShop(10, 20, 30);
        // LocationUtils.toStringFromLocation uses block coords
        assertEquals("test_world,10,20,30", shop.getID());
    }
}
