package ryuzuinfiniteshop.ryuzuinfiniteshop.command;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CommandChainTest {

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
    void registerCommandDoesNotThrow() {
        assertDoesNotThrow(CommandChain::registerCommand);
    }

    @Test
    void multipleRegisterDoesNotThrow() {
        assertDoesNotThrow(CommandChain::registerCommand);
        assertDoesNotThrow(CommandChain::registerCommand);
    }
}
