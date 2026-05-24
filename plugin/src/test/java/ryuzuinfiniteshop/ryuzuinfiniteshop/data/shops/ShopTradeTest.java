package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ShopTradeTest {

    private static ServerMock server;
    private ShopTrade trade;
    private ItemStack diamond;
    private ItemStack emerald;

    @BeforeAll
    static void setUpClass() throws Exception {
        server = MockBukkit.mock();
        var plugin = org.mockito.Mockito.mock(RyuZUInfiniteShop.class);
        org.mockito.Mockito.when(plugin.namespace()).thenReturn("searchableinfiniteshop");
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    @AfterAll
    static void tearDownClass() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        diamond = new ItemStack(Material.DIAMOND, 3);
        emerald = new ItemStack(Material.EMERALD, 1);
        trade = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
    }

    @Test
    void constructorSetsGiveItems() {
        ItemStack[] give = trade.getGiveItems();
        assertEquals(1, give.length);
        assertTrue(diamond.isSimilar(give[0]));
        assertEquals(3, give[0].getAmount());
    }

    @Test
    void constructorSetsTakeItems() {
        ItemStack[] take = trade.getTakeItems();
        assertEquals(1, take.length);
        assertTrue(emerald.isSimilar(take[0]));
    }

    @Test
    void emptyGiveItems() {
        ShopTrade empty = new ShopTrade(new ItemStack[]{}, new ItemStack[]{new ItemStack(Material.DIAMOND)});
        assertEquals(0, empty.getGiveItems().length);
    }

    @Test
    void multipleItemsInTrade() {
        ItemStack[] give = {
            new ItemStack(Material.DIAMOND, 1),
            new ItemStack(Material.EMERALD, 2)
        };
        ItemStack[] take = {
            new ItemStack(Material.IRON_INGOT, 5)
        };
        ShopTrade multi = new ShopTrade(give, take);
        assertEquals(2, multi.getGiveItems().length);
        assertEquals(1, multi.getTakeItems().length);
    }

    @Test
    void tradeEquality() {
        ShopTrade trade1 = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade trade2 = new ShopTrade(new ItemStack[]{diamond.clone()}, new ItemStack[]{emerald.clone()});
        assertEquals(trade1, trade2);
    }

    @Test
    void tradeInequality() {
        ShopTrade different = new ShopTrade(new ItemStack[]{new ItemStack(Material.IRON_INGOT)}, new ItemStack[]{emerald});
        assertNotEquals(trade, different);
    }

    @Test
    void hashcodeConsistency() {
        ShopTrade trade1 = new ShopTrade(new ItemStack[]{diamond}, new ItemStack[]{emerald});
        ShopTrade trade2 = new ShopTrade(new ItemStack[]{diamond.clone()}, new ItemStack[]{emerald.clone()});
        assertEquals(trade1.hashCode(), trade2.hashCode());
    }

    @Test
    void getFirstGiveTakeItem() {
        var entry = trade.getFirstGiveTakeItem();
        assertTrue(emerald.isSimilar(entry.getKey()));
        assertTrue(diamond.isSimilar(entry.getValue()));
    }

    @Test
    void getLimitWithoutUuidReturnsZero() {
        assertEquals(0, trade.getLimit());
    }

    @Test
    void getOptionReturnsDefaultForNoUuid() {
        assertNotNull(trade.getOption());
        assertTrue(trade.getOption().isNoData());
    }

    @Test
    void setTradeUpdatesTrade() {
        ItemStack[] newGive = {new ItemStack(Material.EMERALD, 10)};
        ItemStack[] newTake = {new ItemStack(Material.DIAMOND, 1)};
        ShopTrade replacement = new ShopTrade(newGive, newTake);
        trade.setTrade(replacement);
        assertTrue(new ItemStack(Material.EMERALD, 10).isSimilar(trade.getGiveItems()[0]));
    }

    @Test
    void deserializeFromConfig() {
        HashMap<String, Object> config = new HashMap<>();
        ItemStack[] give = {new ItemStack(Material.DIAMOND, 1)};
        ItemStack[] take = {new ItemStack(Material.EMERALD, 5)};
        ShopTrade original = new ShopTrade(give, take);
        ConfigurationSection serialized = original.serialize();
        HashMap<String, Object> map = new HashMap<>();
        map.put("give", serialized.get("give"));
        map.put("take", serialized.get("take"));
        ShopTrade deserialized = new ShopTrade(map);
        assertEquals(original, deserialized);
    }

    @Test
    void serializationRoundTrip() {
        ConfigurationSection serialized = trade.serialize();
        assertNotNull(serialized.get("give"));
        assertNotNull(serialized.get("take"));
    }

    @Test
    void nullTradeInSetTradeDoesNothing() {
        trade.setTrade(null);
        assertTrue(new ItemStack(Material.DIAMOND, 3).isSimilar(trade.getGiveItems()[0]));
    }
}
