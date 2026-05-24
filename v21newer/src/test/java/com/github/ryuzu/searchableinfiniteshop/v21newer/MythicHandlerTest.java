package com.github.ryuzu.searchableinfiniteshop.v21newer;

import com.github.ryuzu.searchableinfiniteshop.api.IMythicHandler;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MythicHandlerTest {

    private static ServerMock server;
    private IMythicHandler handler;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void createHandler() {
        handler = new MythicHandlerV5_12_0();
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
