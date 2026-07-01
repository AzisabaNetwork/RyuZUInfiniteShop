package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Base class for Shop tests providing shared MockBukkit setup.
 */
public abstract class ShopTestBase {

    protected static ServerMock server;
    protected static WorldMock world;
    protected static Path tempDir;

    /**
     * Cleans up shop YAML files to avoid cross-test contamination.
     */
    protected static void cleanShopFiles() {
        File shopsDir = new File(tempDir.toFile(), "shops");
        if (shopsDir.exists()) {
            try {
                Files.walk(shopsDir.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                // ignore cleanup errors
            }
        }
    }

    @BeforeAll
    static void baseSetUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");

        // Create temp directory for plugin data
        tempDir = Files.createTempDirectory("sis-shop-test");

        // Mock the plugin instance
        var plugin = org.mockito.Mockito.mock(RyuZUInfiniteShop.class);
        org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
        org.mockito.Mockito.when(plugin.getName()).thenReturn("SearchableInfiniteShop");
        org.mockito.Mockito.when(plugin.namespace()).thenReturn("searchableinfiniteshop");

        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    @AfterAll
    static void baseTearDown() throws Exception {
        // Clean up temp dir
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        // Reset static ShopUtil state
        ShopUtil.getShops().clear();

        // Reset plugin field to avoid interfering with other test classes
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, null);

        MockBukkit.unmock();
    }

    /**
     * Creates a simple Shop at the given coordinates with default entity type.
     */
    protected static Shop createSimpleShop(int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        ConfigurationSection config = new MemoryConfiguration();
        return new Shop(loc, "ZOMBIE", config);
    }

    /**
     * Creates a Shop with a specific entity type at the given coordinates.
     */
    protected static Shop createShop(int x, int y, int z, String entityType) {
        Location loc = new Location(world, x, y, z);
        ConfigurationSection config = new MemoryConfiguration();
        return new Shop(loc, entityType, config);
    }
}
