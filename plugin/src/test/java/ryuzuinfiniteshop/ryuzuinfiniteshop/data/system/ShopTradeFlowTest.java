package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.VaultHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;

/**
 * Integration-style tests for the full trade flow (ShopTrade.trade + getResult).
 */
class ShopTradeFlowTest {

    private static ServerMock server;
    private PlayerMock player;
    private ItemStack diamond;
    private ItemStack emerald;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        // Mock the plugin instance for NamespacedKey usage in NBTUtil
        var plugin = mock(RyuZUInfiniteShop.class);
        when(plugin.namespace()).thenReturn("searchableinfiniteshop");
        when(plugin.getName()).thenReturn("SearchableInfiniteShop");
        when(plugin.getDataFolder()).thenReturn(Files.createTempDirectory("sis-trade-flow-test").toFile());
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUpPlayer() throws IOException {
        player = server.addPlayer("test_player");
        player.getInventory().clear();
        diamond = new ItemStack(Material.DIAMOND, 3);
        emerald = new ItemStack(Material.EMERALD, 1);
        // Clear static state from ShopTrade to avoid cross-test contamination
        ShopTrade.tradeUUID.clear();
        ShopTrade.tradeCounts.rowKeySet().forEach(key -> ShopTrade.tradeCounts.row(key).clear());
        ShopTrade.tradeOptions.clear();
        // Pre-create valid options.yml for saveTradeOption()
        Path optionsFile = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "options.yml").toPath();
        Files.createDirectories(optionsFile.getParent());
        Files.writeString(optionsFile, "{}");
    }

    // ========== getResult tests (no mockStatic needed) ==========

    private static int countItems(org.bukkit.inventory.Inventory inv, Material type) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == type) {
                count += item.getAmount();
            }
        }
        return count;
    }

    @Test
    void getResultSuccessWhenPlayerHasItems() {
        player.getInventory().addItem(diamond);
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});

        assertEquals(ShopTrade.TradeResult.Success, trade.getResult(player));
    }

    @Test
    void getResultNotEnoughItems() {
        // Player has no items
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});

        assertEquals(ShopTrade.TradeResult.NotEnoughItems, trade.getResult(player));
    }

    @Test
    void getResultNotEnoughItemsPartial() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        // Trade requires 3 diamonds, player has 1
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{new ItemStack(Material.DIAMOND, 3)});

        assertEquals(ShopTrade.TradeResult.NotEnoughItems, trade.getResult(player));
    }

    @Test
    void getResultNotEnoughMoney() {
        try (var vaultMock = mockStatic(VaultHandler.class)) {
            vaultMock.when(() -> VaultHandler.hasMoney(any(), anyDouble())).thenReturn(false);

            player.getInventory().addItem(diamond);
            ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
            trade.setTradeOption(new TradeOption(false, 100.0, 0, false, 100), false);

            assertEquals(ShopTrade.TradeResult.NotEnoughMoney, trade.getResult(player));
        }
    }

    @Test
    void getResultLimited() {
        player.getInventory().addItem(diamond);
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
        trade.setTradeOption(new TradeOption(false, 0, 1, false, 100), false);
        // Set trade count to limit
        trade.setTradeCount(player, 1);

        assertEquals(ShopTrade.TradeResult.Limited, trade.getResult(player));
    }

    @Test
    void getResultSuccessWithNoMoneyOption() {
        player.getInventory().addItem(diamond);
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
        // Default option (money=0, limit=0, rate=100) → no constraints

        assertEquals(ShopTrade.TradeResult.Success, trade.getResult(player));
    }

    @Test
    void getResultFullInventory() {
        // Fill player's inventory completely (36 storage + hotbar + offhand)
        ItemStack filler = new ItemStack(Material.DIRT, 64);
        for (int i = 0; i < 40; i++) {
            player.getInventory().setItem(i, filler);
        }
        player.getInventory().setItemInOffHand(filler);
        // Trade gives emerald, but no space
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{new ItemStack(Material.DIAMOND, 1)});
        // Put diamonds in main hand (the only slot with space)
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 1));

        assertEquals(ShopTrade.TradeResult.Full, trade.getResult(player));
    }

    // ========== getResult with shop context ==========

    @Test
    void getResultWithLockedShop() {
        player.getInventory().addItem(diamond);
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
        // No shop context → locked not checked, so result is Success
        assertEquals(ShopTrade.TradeResult.Success, trade.getResult(player));
    }

    // ========== trade() method tests (basic flow without Vault mock) ==========

    @Test
    void tradeReturnsZeroWhenNotEnoughItems() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});

        int result = trade.trade(player, 1);

        assertEquals(0, result, "No trades should succeed when items are missing");
    }

    @Test
    void tradeRemovesItemsAndGivesItemsOnSuccess() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        ItemStack[] give = {new ItemStack(Material.EMERALD, 1)};
        ItemStack[] take = {new ItemStack(Material.DIAMOND, 3)};
        ShopTrade trade = new ShopTrade(give, take);

        int result = trade.trade(player, 1);

        assertEquals(1, result, "One trade should succeed");
        // Player should have lost 3 diamonds and gained 1 emerald
        assertFalse(ItemUtil.contains(player.getInventory(), new ItemStack(Material.DIAMOND, 3)),
                "Player should not have 3 diamonds anymore");
        assertTrue(player.getInventory().contains(Material.EMERALD),
                "Player should have at least 1 emerald");
    }

    @Test
    void tradeMultipleTimes() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 12));
        ItemStack[] give = {new ItemStack(Material.EMERALD, 1)};
        ItemStack[] take = {new ItemStack(Material.DIAMOND, 3)};
        ShopTrade trade = new ShopTrade(give, take);

        int result = trade.trade(player, 4);

        assertEquals(4, result, "All 4 trades should succeed");
        assertTrue(player.getInventory().contains(Material.EMERALD),
                "Player should have emeralds");
        assertEquals(4, countItems(player.getInventory(), Material.EMERALD));
    }

    @Test
    void tradeStopsWhenItemsRunOut() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 7));
        // 7 diamonds = 2 full trades (3 each) with 1 leftover
        ItemStack[] give = {new ItemStack(Material.EMERALD, 1)};
        ItemStack[] take = {new ItemStack(Material.DIAMOND, 3)};
        ShopTrade trade = new ShopTrade(give, take);

        int result = trade.trade(player, 5);

        assertEquals(2, result, "Only 2 trades should succeed (7 diamonds / 3 = 2 remainder 1)");
        assertTrue(player.getInventory().contains(Material.EMERALD),
                "Player should have emeralds");
        assertEquals(2, countItems(player.getInventory(), Material.EMERALD));
    }

    // ========== Trade limit tests ==========

    @Test
    void tradeRespectsLimit() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 12));
        ItemStack[] give = {new ItemStack(Material.EMERALD, 1)};
        ItemStack[] take = {new ItemStack(Material.DIAMOND, 3)};
        ShopTrade trade = new ShopTrade(give, take);
        trade.setTradeOption(new TradeOption(false, 0, 2, false, 100), false);

        int result1 = trade.trade(player, 5);
        assertEquals(2, result1, "Should stop at limit=2");
    }

    @Test
    void getResultAfterReachingLimit() {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 12));
        ItemStack[] give = {new ItemStack(Material.EMERALD, 1)};
        ItemStack[] take = {new ItemStack(Material.DIAMOND, 3)};
        ShopTrade trade = new ShopTrade(give, take);
        trade.setTradeOption(new TradeOption(false, 0, 2, false, 100), false);

        trade.setTradeCount(player, 2);

        assertEquals(ShopTrade.TradeResult.Limited, trade.getResult(player));
    }

    // ========== Money-related tests with VaultHandler mock ==========

    @Test
    void getResultNotEnoughMoneyWithVaultMock() {
        try (var vaultMock = mockStatic(VaultHandler.class)) {
            vaultMock.when(() -> VaultHandler.hasMoney(any(), anyDouble())).thenReturn(false);

            player.getInventory().addItem(diamond);
            ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
            trade.setTradeOption(new TradeOption(false, 50.0, 0, false, 100), false);

            assertEquals(ShopTrade.TradeResult.NotEnoughMoney, trade.getResult(player));
        }
    }

    @Test
    void getResultSuccessWithEnoughMoney() {
        try (var vaultMock = mockStatic(VaultHandler.class)) {
            vaultMock.when(() -> VaultHandler.hasMoney(any(), anyDouble())).thenReturn(true);

            player.getInventory().addItem(diamond);
            ShopTrade trade = new ShopTrade(new ItemStack[]{emerald}, new ItemStack[]{diamond});
            trade.setTradeOption(new TradeOption(false, 50.0, 0, false, 100), false);

            assertEquals(ShopTrade.TradeResult.Success, trade.getResult(player));
        }
    }
}
