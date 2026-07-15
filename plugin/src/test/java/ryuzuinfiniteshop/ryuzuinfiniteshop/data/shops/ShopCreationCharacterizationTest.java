package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for Shop creation and basic state.
 * These capture the current behavior to serve as a refactoring safety net.
 */
class ShopCreationCharacterizationTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    @Test
    void createSimpleShop() {
        Shop shop = createSimpleShop(10, 20, 30);

        assertNotNull(shop);
        assertEquals("test_world,10,20,30", shop.getID());
        assertEquals(NpcType.NORMAL, shop.getNpcType());
        assertEquals(ShopType.TwotoOne, shop.getShopType());
        assertFalse(shop.isLock());
        assertTrue(shop.isSearchable());  // YAML default: true
        assertFalse(shop.isInvisible());
        assertFalse(shop.isEditting());
    }

    @Test
    void createBlockShop() {
        Shop shop = createShop(0, 64, 0, "BLOCK");

        assertNotNull(shop);
        assertEquals("test_world,0,64,0", shop.getID());
        assertEquals(NpcType.BLOCK, shop.getNpcType());
    }

    @Test
    void createVillagerShopHasDefaultVillagerData() {
        Shop shop = ShopUtil.createNewShop(new org.bukkit.Location(world, 0, 65, 0), "VILLAGER", null);

        assertInstanceOf(VillagerableShop.class, shop);
        VillagerableShop villagerShop = (VillagerableShop) shop;
        assertEquals(org.bukkit.entity.Villager.Profession.NONE, villagerShop.getProfession());
        assertEquals(org.bukkit.entity.Villager.Type.PLAINS, villagerShop.getBiome());
    }

    @Test
    void shopIsRegisteredInShopUtil() {
        Shop shop = createSimpleShop(5, 10, 15);

        assertSame(shop, ShopUtil.getShop("test_world,5,10,15"));
        assertTrue(ShopUtil.getShops().containsKey("test_world,5,10,15"));
    }

    @Test
    void multipleShopsHaveDifferentIds() {
        Shop shop1 = createSimpleShop(1, 1, 1);
        Shop shop2 = createSimpleShop(2, 2, 2);

        assertNotEquals(shop1.getID(), shop2.getID());
    }

    @Test
    void shopHasNoTradePagesWhenEmpty() {
        Shop shop = createSimpleShop(100, 100, 100);

        // Empty shop has no trade pages
        assertNull(shop.getPage(1));
        assertEquals(0, shop.getPageCount());
    }

    @Test
    void shopHasInitializedEditorPages() {
        Shop shop = createSimpleShop(200, 200, 200);

        assertNotNull(shop.getEditor(1));
    }

    @Test
    void shopWithSameLocationIsEqual() {
        Shop shop1 = createSimpleShop(50, 50, 50);
        Shop shop2 = createSimpleShop(50, 50, 50);

        // Two shops at the same location should be equal (by ID)
        assertEquals(shop1, shop2);
    }

    @Test
    void shopAtDifferentLocationsAreNotEqual() {
        Shop shop1 = createSimpleShop(1, 1, 1);
        Shop shop2 = createSimpleShop(2, 2, 2);

        assertNotEquals(shop1, shop2);
    }
}
