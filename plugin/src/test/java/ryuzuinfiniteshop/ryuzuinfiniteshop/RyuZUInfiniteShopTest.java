package ryuzuinfiniteshop.ryuzuinfiniteshop;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RyuZUInfiniteShopTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @Order(1)
    void prefixCommandIsSet() {
        assertEquals("§6[SIS]", RyuZUInfiniteShop.prefixCommand);
    }

    @Test
    @Order(2)
    void prefixPersistentIsSet() {
        assertEquals("RyuZU.Infinite.Shop.", RyuZUInfiniteShop.prefixPersistent);
    }

    @Test
    @Order(3)
    void pluginInitiallyNullBeforeEnable() {
        assertNull(RyuZUInfiniteShop.getPlugin());
    }

    @Test
    @Order(4)
    void serverMockIsNotNull() {
        assertNotNull(server);
        assertTrue(MockBukkit.isMocked());
    }
}
