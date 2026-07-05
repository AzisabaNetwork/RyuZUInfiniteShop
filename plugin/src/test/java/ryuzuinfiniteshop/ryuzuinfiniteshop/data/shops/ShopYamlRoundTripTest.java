package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for Shop YAML round-trip (save → load → verify).
 */
class ShopYamlRoundTripTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    @Test
    void saveYamlCreatesFileOnDisk() {
        Shop shop = createSimpleShop(1, 64, 1);
        shop.saveYaml();

        File expectedFile = new File(tempDir.toFile(), "shops/test_world,1,64,1.yml");
        assertTrue(expectedFile.exists(), "YAML file should exist after saveYaml()");
    }

    @Test
    void saveThenLoadRoundTripPreservesShopType() {
        Shop shop = createSimpleShop(2, 64, 2);
        shop.changeShopType(); // TwotoOne → FourtoFour
        shop.saveYaml();

        // Create a new shop at the same location to trigger load
        Shop loaded = createSimpleShop(2, 64, 2);
        assertEquals(ShopType.FourtoFour, loaded.getShopType(),
                "Loaded shop should preserve the saved ShopType");
    }

    @Test
    void saveThenLoadRoundTripPreservesLockStatus() {
        Shop shop = createSimpleShop(3, 64, 3);
        shop.setLock(true);
        shop.saveYaml();

        Shop loaded = createSimpleShop(3, 64, 3);
        assertTrue(loaded.isLock(), "Loaded shop should preserve lock=true");
    }

    @Test
    void saveThenLoadRoundTripPreservesSearchable() {
        Shop shop = createSimpleShop(4, 64, 4);
        shop.setSearchable(false);
        shop.saveYaml();

        Shop loaded = createSimpleShop(4, 64, 4);
        assertFalse(loaded.isSearchable(), "Loaded shop should preserve searchable=false");
    }

    @Test
    void saveThenLoadRoundTripPreservesInvisible() {
        Shop shop = createSimpleShop(5, 64, 5);
        shop.setInvisible(true);
        shop.saveYaml();

        Shop loaded = createSimpleShop(5, 64, 5);
        assertTrue(loaded.isInvisible(), "Loaded shop should preserve invisible=true");
    }

    @Test
    void getFileReturnsCorrectPath() {
        Shop shop = createSimpleShop(100, 200, 300);
        File file = shop.getFile();

        assertNotNull(file);
        assertTrue(file.getPath().replace("\\", "/").contains("shops/test_world,100,200,300.yml"),
                "File path should contain shops/ and the shop ID");
    }
}
