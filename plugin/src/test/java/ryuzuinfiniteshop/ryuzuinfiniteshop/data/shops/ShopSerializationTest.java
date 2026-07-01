package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for Shop YAML serialization (saveYaml / getSaveYamlProcess).
 * These capture the exact YAML format produced by the current implementation.
 */
class ShopSerializationTest extends ShopTestBase {

    @BeforeEach
    void cleanUp() {
        ShopUtil.getShops().clear();
        cleanShopFiles();
    }

    @Test
    void saveYamlProducesValidYaml() {
        Shop shop = createSimpleShop(10, 64, 10);
        YamlConfiguration yaml = shop.saveYaml();

        assertNotNull(yaml);
        // Should have no errors when saving
    }

    @Test
    void saveYamlContainsNpcOptionsSection() {
        Shop shop = createSimpleShop(20, 64, 20);
        YamlConfiguration yaml = shop.saveYaml();

        // null-valued keys are removed by YamlConfiguration.set(path, null)
        // MythicMob and Citizen are null for a simple NORMAL shop
        assertNull(yaml.get("Npc.Options.MythicMob"));
        assertNull(yaml.get("Npc.Options.Citizen"));
        assertTrue(yaml.contains("Npc.Options.DisplayName"));
        assertTrue(yaml.contains("Npc.Options.EntityType"));
        assertTrue(yaml.contains("Npc.Options.Invisible"));
    }

    @Test
    void saveYamlContainsShopOptionsSection() {
        Shop shop = createSimpleShop(30, 64, 30);
        YamlConfiguration yaml = shop.saveYaml();

        assertTrue(yaml.contains("Shop.Options.ShopType"));
        assertEquals("TwotoOne", yaml.getString("Shop.Options.ShopType"));
    }

    @Test
    void saveYamlContainsNpcStatusSection() {
        Shop shop = createSimpleShop(40, 64, 40);
        YamlConfiguration yaml = shop.saveYaml();

        assertTrue(yaml.contains("Npc.Status.Lock"));
        assertTrue(yaml.contains("Npc.Status.Searchable"));
        assertTrue(yaml.contains("Npc.Status.Yaw"));

        assertEquals(false, yaml.getBoolean("Npc.Status.Lock"));
    }

    @Test
    void saveYamlContainsTradesSection() {
        Shop shop = createSimpleShop(50, 64, 50);
        YamlConfiguration yaml = shop.saveYaml();

        assertTrue(yaml.contains("Trades"));
        assertEquals(0, yaml.getList("Trades").size());
    }

    @Test
    void saveYamlEquipmentsSection() {
        Shop shop = createSimpleShop(60, 64, 60);
        YamlConfiguration yaml = shop.saveYaml();

        assertTrue(yaml.contains("Npc.Options.Equipments"));
        assertNotNull(yaml.get("Npc.Options.Equipments"));
    }

    @Test
    void saveYamlWithDefaultSpawningOptions() {
        Shop shop = createSimpleShop(70, 64, 70);
        YamlConfiguration yaml = shop.saveYaml();

        // Entity type should be set
        assertEquals("ZOMBIE", yaml.getString("Npc.Options.EntityType"));
    }
}
