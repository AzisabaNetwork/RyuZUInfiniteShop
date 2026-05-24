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
    void versionIsParsedOnClassLoad() {
        assertTrue(RyuZUInfiniteShop.VERSION > 0);
    }

    @Test
    @Order(2)
    void prefixCommandIsSet() {
        assertEquals("§6[SIS]", RyuZUInfiniteShop.prefixCommand);
    }

    @Test
    @Order(3)
    void prefixPersistentIsSet() {
        assertEquals("RyuZU.Infinite.Shop.", RyuZUInfiniteShop.prefixPersistent);
    }

    @Test
    @Order(4)
    void pluginInitiallyNullBeforeEnable() {
        assertNull(RyuZUInfiniteShop.getPlugin());
    }

    @Test
    @Order(5)
    void serverMockIsNotNull() {
        assertNotNull(server);
        assertTrue(MockBukkit.isMocked());
    }
}
