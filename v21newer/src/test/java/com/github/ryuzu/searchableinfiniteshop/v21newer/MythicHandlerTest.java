package com.github.ryuzu.searchableinfiniteshop.v21newer;

import com.github.ryuzu.searchableinfiniteshop.api.IMythicHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MythicHandlerTest {

    private static ServerMock server;
    private static JavaPlugin plugin;
    private IMythicHandler handler;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("MythicMobs");
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void createHandler() {
        handler = new MythicHandlerV5_12_0(plugin, runnable -> {});
    }

    @Test
    @Order(1)
    void handlerIsNotNull() {
        assertNotNull(handler);
    }

    @Test
    @Order(2)
    void handlerImplementsIMythicHandler() {
        assertInstanceOf(IMythicHandler.class, handler);
    }
}
