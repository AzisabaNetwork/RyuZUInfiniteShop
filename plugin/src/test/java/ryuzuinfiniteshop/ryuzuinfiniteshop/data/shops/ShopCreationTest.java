package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopCreationTest {

    private static ServerMock server;
    private static World world;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @Order(1)
    void shopsMapInitiallyEmpty() {
        assertTrue(ShopUtil.getShops().isEmpty());
    }
}
