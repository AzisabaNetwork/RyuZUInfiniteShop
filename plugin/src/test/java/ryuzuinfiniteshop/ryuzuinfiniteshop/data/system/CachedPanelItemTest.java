package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The panel items are cached, so they must be handed out as copies: a caller that mutates the returned stack
 * (rename, lore, amount) must not affect every other panel drawn afterwards.
 */
class CachedPanelItemTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        var plugin = org.mockito.Mockito.mock(RyuZUInfiniteShop.class);
        org.mockito.Mockito.when(plugin.namespace()).thenReturn("searchableinfiniteshop");
        org.mockito.Mockito.when(plugin.getName()).thenReturn("SearchableInfiniteShop");
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    @AfterAll
    static void tearDown() throws Exception {
        // Other tests assert that the plugin field is null until onEnable; do not leak the mock.
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void getFilterReturnsIndependentCopies() {
        ItemStack first = ShopTrade.getFilter();
        first.setAmount(42);
        first.setType(Material.STONE);

        ItemStack second = ShopTrade.getFilter();
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, second.getType());
        assertEquals(1, second.getAmount());
        assertNotSame(first, second);
    }

    @Test
    void getWhitePanelReturnsIndependentCopies() {
        ItemStack first = ItemUtil.getWhitePanel();
        first.setAmount(42);

        ItemStack second = ItemUtil.getWhitePanel();
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, second.getType());
        assertEquals(1, second.getAmount());
        assertNotSame(first, second);
    }

    @Test
    void panelKeepsTheNameItIsBuiltWith() {
        String black = ItemUtil.getNamedItem(ItemUtil.getColoredItem("BLACK_STAINED_GLASS_PANE"),
                ChatColor.BLACK + "").getItemMeta().getDisplayName();
        String white = ItemUtil.getNamedItem(ItemUtil.getColoredItem("WHITE_STAINED_GLASS_PANE"),
                ChatColor.WHITE + "").getItemMeta().getDisplayName();

        assertEquals(black, ShopTrade.getFilter().getItemMeta().getDisplayName());
        assertEquals(white, ItemUtil.getWhitePanel().getItemMeta().getDisplayName());
    }
}
