package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.searchableinfiniteshop.api.IMythicHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MythicInstanceProviderTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void resetProvider() throws Exception {
        Field instanceField = MythicInstanceProvider.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void notLoadedWhenMythicMobsPluginMissing() {
        MythicInstanceProvider.setInstance();

        assertFalse(MythicInstanceProvider.isLoaded());
    }

    @Test
    void getInstanceThrowsWhenNotLoaded() {
        assertFalse(MythicInstanceProvider.isLoaded());
        assertThrows(NullPointerException.class, () -> MythicInstanceProvider.getInstance());
    }

    @Test
    void getInstanceReturnsCachedInstanceWhenPluginEnabled() throws Exception {
        IMythicHandler mockHandler = mock(IMythicHandler.class);
        JavaPlugin enabledPlugin = mock(JavaPlugin.class);
        when(enabledPlugin.isEnabled()).thenReturn(true);
        when(mockHandler.getPlugin()).thenReturn(enabledPlugin);

        setProviderInstance(mockHandler);

        assertSame(mockHandler, MythicInstanceProvider.getInstance());
    }

    @Test
    void getInstanceKeepsCachedHandlerWhenReinitializationHasNoMythicMobs() throws Exception {
        IMythicHandler mockHandler = mock(IMythicHandler.class);
        JavaPlugin disabledPlugin = mock(JavaPlugin.class);
        when(disabledPlugin.isEnabled()).thenReturn(false);
        when(mockHandler.getPlugin()).thenReturn(disabledPlugin);

        setProviderInstance(mockHandler);

        // Without a real MythicMobs plugin, reinitialization cannot replace the cached handler.
        assertSame(mockHandler, MythicInstanceProvider.getInstance());
    }

    private static void setProviderInstance(IMythicHandler handler) throws Exception {
        Field instanceField = MythicInstanceProvider.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, handler);
    }
}
