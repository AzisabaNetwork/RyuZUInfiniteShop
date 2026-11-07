package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shops in a world that is not loaded yet must not abort loading and must be
 * loaded once the world appears.
 */
class DeferredWorldShopLoadTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    @AfterEach
    void resetDeferred() {
        cleanShopFiles();
        ShopUtil.loadAllShops(); // clears registry and deferred state
        ShopUtil.getShops().clear();
    }

    private File copyShopFileToWorld(Shop source, String worldName) throws Exception {
        source.saveYaml();
        File shops = new File(tempDir.toFile(), "shops");
        File target = new File(shops, worldName + ",1,64,1.yml");
        Files.copy(new File(shops, source.getID() + ".yml").toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    @Test
    void shopInUnloadedWorldIsDeferredWithoutAbortingOtherShops() throws Exception {
        Shop template = createSimpleShop(1, 64, 1);
        copyShopFileToWorld(template, "late_world");
        // A shop file sorted after the deferred one must still load.
        createSimpleShop(9, 64, 9).saveYaml();

        ShopUtil.loadAllShops();

        assertTrue(ShopUtil.getShops().containsKey("test_world,1,64,1"));
        assertTrue(ShopUtil.getShops().containsKey("test_world,9,64,9"));
        assertFalse(ShopUtil.getShops().containsKey("late_world,1,64,1"));
        assertEquals(1, ShopUtil.getDeferredShopCount());
    }

    @Test
    void deferredShopLoadsWhenWorldLoads() throws Exception {
        Shop template = createSimpleShop(1, 64, 1);
        copyShopFileToWorld(template, "shop");
        ShopUtil.loadAllShops();
        assertFalse(ShopUtil.getShops().containsKey("shop,1,64,1"));

        server.addSimpleWorld("shop");
        List<Shop> loaded = ShopUtil.loadDeferredShops("shop");

        assertEquals(1, loaded.size());
        assertTrue(ShopUtil.getShops().containsKey("shop,1,64,1"));
        assertEquals(0, ShopUtil.getDeferredShopCount());
    }

    @Test
    void unloadingWorldDefersItsShopsAgain() throws Exception {
        server.addSimpleWorld("unload_world");
        Shop template = createSimpleShop(1, 64, 1);
        copyShopFileToWorld(template, "unload_world");
        ShopUtil.loadAllShops();
        assertTrue(ShopUtil.getShops().containsKey("unload_world,1,64,1"));

        ShopUtil.unloadShops("unload_world");

        assertFalse(ShopUtil.getShops().containsKey("unload_world,1,64,1"));
        assertTrue(ShopUtil.getShops().containsKey("test_world,1,64,1"));
        assertEquals(1, ShopUtil.getDeferredShopCount());
    }

    @Test
    void tradeIdsOfDeferredShopsAreKept() throws Exception {
        Shop template = createSimpleShop(1, 64, 1);
        File file = copyShopFileToWorld(template, "missing_world");
        UUID tradeId = UUID.randomUUID();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("Trades", List.of(Map.of("uuid", tradeId.toString())));
        yaml.save(file);

        ShopUtil.loadAllShops();

        assertTrue(ShopUtil.getDeferredTradeUUIDs().contains(tradeId));
    }
}
