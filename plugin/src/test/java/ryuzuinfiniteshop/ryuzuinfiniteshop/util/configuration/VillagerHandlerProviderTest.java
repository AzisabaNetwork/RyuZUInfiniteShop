package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class VillagerHandlerProviderTest {

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
    void providesNonNullHandler() {
        IVillagerHandler handler = VillagerHandlerProvider.getHandler();
        assertNotNull(handler);
    }

    @Test
    void cachesHandlerInstance() {
        IVillagerHandler first = VillagerHandlerProvider.getHandler();
        IVillagerHandler second = VillagerHandlerProvider.getHandler();
        assertSame(first, second);
    }

    @Test
    void handlerHasBiomeSupport() {
        IVillagerHandler handler = VillagerHandlerProvider.getHandler();
        assertTrue(handler.hasBiome());
    }

    @Test
    void handlerMapsLegacyProfessions() {
        IVillagerHandler handler = VillagerHandlerProvider.getHandler();
        assertEquals("NONE", handler.resolveProfession("NORMAL"));
        assertEquals("CLERIC", handler.resolveProfession("PRIEST"));
        assertEquals("ARMORER", handler.resolveProfession("BLACKSMITH"));
    }
}
