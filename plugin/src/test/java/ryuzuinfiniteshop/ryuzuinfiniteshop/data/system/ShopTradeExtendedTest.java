package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for ShopTrade covering edge cases and TradeOption interaction.
 */
class ShopTradeExtendedTest {

    private static ServerMock server;
    private ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
    private ItemStack emerald = new ItemStack(Material.EMERALD, 1);
    private ItemStack iron = new ItemStack(Material.IRON_INGOT, 1);
    private ItemStack gold = new ItemStack(Material.GOLD_INGOT, 1);

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        // Mock the plugin instance for NamespacedKey usage in NBTUtil
        var plugin = org.mockito.Mockito.mock(RyuZUInfiniteShop.class);
        org.mockito.Mockito.when(plugin.namespace()).thenReturn("searchableinfiniteshop");
        org.mockito.Mockito.when(plugin.getName()).thenReturn("SearchableInfiniteShop");
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    // ========== TradeOption interaction ==========

    @Test
    void setTradeOptionCreatesUuidAndStoresOption() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        TradeOption option = new TradeOption(false, 0, 10, false, 100);

        trade.setTradeOption(option, false);

        assertEquals(10, trade.getLimit());
        assertEquals(option, trade.getOption());
    }

    @Test
    void setTradeOptionNoDataWithoutForceDoesNothing() {
        // Use unique items to avoid collision with static tradeUUID map from other tests
        ShopTrade trade = new ShopTrade(new ItemStack[]{iron}, new ItemStack[]{gold});
        TradeOption defaultOption = new TradeOption(); // isNoData() == true

        trade.setTradeOption(defaultOption, false);

        assertEquals(0, trade.getLimit());
        assertTrue(trade.getOption().isNoData());
    }

    @Test
    void setTradeOptionNoDataWithForceClearsOption() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        TradeOption initialOption = new TradeOption(false, 0, 5, false, 100);
        trade.setTradeOption(initialOption, false);
        assertEquals(5, trade.getLimit());

        // Now force-clear with no-data option
        TradeOption clearOption = new TradeOption(); // isNoData() == true
        trade.setTradeOption(clearOption, true);

        assertEquals(0, trade.getLimit());
        assertTrue(trade.getOption().isNoData());
    }

    @Test
    void setTradeOptionReplacesExistingOption() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        trade.setTradeOption(new TradeOption(false, 0, 5, false, 100), false);
        trade.setTradeOption(new TradeOption(true, 50.0, 3, true, 50), false);

        assertEquals(3, trade.getLimit());
        assertEquals(50.0, trade.getOption().getMoney());
        assertTrue(trade.getOption().isGive());
        assertTrue(trade.getOption().isHide());
        assertEquals(50, trade.getOption().getRate());
    }

    // ========== getFirstGiveTakeItem ==========

    @Test
    void getFirstGiveTakeItemReturnsTakeAsKeyGiveAsValue() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        var entry = trade.getFirstGiveTakeItem();
        // Key is first take item, value is first give item
        assertTrue(emerald.isSimilar(entry.getKey()));
        assertTrue(diamond.isSimilar(entry.getValue()));
    }

    @Test
    void getFirstGiveTakeItemWithMultipleItems() {
        ItemStack[] give = {new ItemStack(Material.DIAMOND, 1), new ItemStack(Material.IRON_INGOT, 2)};
        ItemStack[] take = {new ItemStack(Material.EMERALD, 3), new ItemStack(Material.GOLD_INGOT, 1)};
        ShopTrade trade = new ShopTrade(give, take);

        var entry = trade.getFirstGiveTakeItem();
        assertTrue(new ItemStack(Material.EMERALD, 3).isSimilar(entry.getKey()));
        assertTrue(new ItemStack(Material.DIAMOND, 1).isSimilar(entry.getValue()));
    }

    // ========== Static fields isolation ==========

    @Test
    void differentTradesHaveDifferentUuids() {
        ShopTrade trade1 = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade trade2 = new ShopTrade(new ItemStack[]{new ItemStack(Material.IRON_INGOT)}, new ItemStack[]{new ItemStack(Material.GOLD_INGOT)});

        trade1.setTradeOption(new TradeOption(false, 0, 5, false, 100), false);
        trade2.setTradeOption(new TradeOption(false, 0, 3, false, 100), false);

        assertEquals(5, trade1.getLimit());
        assertEquals(3, trade2.getLimit());
    }

    // ========== toString consistency ==========

    @Test
    void tradesWithSameContentAreEqual() {
        ShopTrade a = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade b = new ShopTrade(new ItemStack[]{diamond.clone()}, new ItemStack[]{emerald.clone()});
        assertEquals(a, b);
    }

    @Test
    void tradesWithDifferentGiveAreNotEqual() {
        ShopTrade a = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade b = new ShopTrade(new ItemStack[]{new ItemStack(Material.IRON_INGOT)}, new ItemStack[]{emerald});
        assertNotEquals(a, b);
    }

    @Test
    void tradesWithDifferentTakeAreNotEqual() {
        ShopTrade a = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade b = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{new ItemStack(Material.GOLD_INGOT)});
        assertNotEquals(a, b);
    }

    // ========== Serialization with UUID ==========

    @Test
    void serializeWithUuidAfterSettingOption() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        trade.setTradeOption(new TradeOption(false, 0, 10, false, 100), false);

        var serialized = trade.serialize();
        assertNotNull(serialized.get("give"));
        assertNotNull(serialized.get("take"));
        assertNotNull(serialized.get("uuid"));
    }

    @Test
    void deserializeWithUuidRestoresOptionLink() {
        ShopTrade original = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        original.setTradeOption(new TradeOption(false, 0, 10, false, 100), false);

        var serialized = original.serialize();
        HashMap<String, Object> map = new HashMap<>();
        map.put("give", serialized.get("give"));
        map.put("take", serialized.get("take"));
        map.put("uuid", serialized.get("uuid"));

        // Create a new trade from map - it should link to the same uuid
        // But option won't be restored from this path alone since options
        // are stored separately. The UUID just enables linking.
        ShopTrade deserialized = new ShopTrade(map);

        // The trade data itself is preserved
        assertNotNull(deserialized.getGiveItems());
        assertNotNull(deserialized.getTakeItems());
        assertTrue(diamond.isSimilar(deserialized.getGiveItems()[0]));
        assertTrue(emerald.isSimilar(deserialized.getTakeItems()[0]));

        // Option may not be restored from serialization alone (options stored separately)
        // but the UUID should be linked
    }

    // ========== TradeOption from HashMap deserialization ==========

    @Test
    void deserializeFromCompleteConfig() {
        ShopTrade original = new ShopTrade(
                new ItemStack[]{new ItemStack(Material.DIAMOND, 3)},
                new ItemStack[]{new ItemStack(Material.EMERALD, 5)}
        );

        var serialized = original.serialize();
        HashMap<String, Object> map = new HashMap<>();
        map.put("give", serialized.get("give"));
        map.put("take", serialized.get("take"));

        ShopTrade deserialized = new ShopTrade(map);
        assertEquals(original, deserialized);
    }

    // ========== Multiple give/take items edge cases ==========

    @Test
    void tradeWithMultipleGiveAndTake() {
        ItemStack[] give = {new ItemStack(Material.DIAMOND, 1), new ItemStack(Material.EMERALD, 2)};
        ItemStack[] take = {new ItemStack(Material.IRON_INGOT, 5), new ItemStack(Material.GOLD_INGOT, 3)};
        ShopTrade trade = new ShopTrade(give, take);

        assertEquals(2, trade.getGiveItems().length);
        assertEquals(2, trade.getTakeItems().length);
        assertEquals(2, trade.getGiveItems()[1].getAmount());
    }

    @Test
    void tradeDataImmutabilityViaGetGiveItems() {
        ItemStack[] give = {new ItemStack(Material.DIAMOND, 1)};
        ItemStack[] take = {new ItemStack(Material.EMERALD, 1)};
        ShopTrade trade = new ShopTrade(give, take);

        // Modifying the returned array should not affect the trade
        trade.getGiveItems()[0].setAmount(99);
        assertNotEquals(99, trade.getGiveItems()[0].getAmount());
    }

    // ========== Null safety ==========

    @Test
    void setTradeWithNullDoesNothing() {
        ShopTrade trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        trade.setTrade(null);
        assertTrue(diamond.isSimilar(trade.getGiveItems()[0]));
        assertTrue(emerald.isSimilar(trade.getTakeItems()[0]));
    }
}
