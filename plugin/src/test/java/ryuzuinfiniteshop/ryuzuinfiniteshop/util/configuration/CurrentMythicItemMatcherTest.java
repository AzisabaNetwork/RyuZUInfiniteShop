package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CurrentMythicItemMatcherTest {
    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack item(Material material, String name, List<String> lore, Integer model) {
        ItemStack item = new ItemStack(material, 3);
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        if (model != null) meta.setCustomModelData(model);
        item.setItemMeta(meta);
        return item;
    }

    private static CurrentMythicItemMatcher matcher() {
        AtomicInteger rolls = new AtomicInteger();
        Map<String, Supplier<ItemStack>> current = new HashMap<>();
        current.put("Sword_A", () -> item(Material.DIAMOND_SWORD, "§b§l聖剣", List.of("§7Lv1", "§6攻撃力 10"), 5));
        current.put("Twin_1", () -> item(Material.PAPER, "§aチケット", List.of("§7交換用"), null));
        current.put("Twin_2", () -> item(Material.PAPER, "§aチケット", List.of("§7交換用"), null));
        current.put("Random", () -> item(Material.STICK, "§eくじ", List.of("§7" + rolls.incrementAndGet()), null));
        current.put("Plain", () -> new ItemStack(Material.STONE));
        return CurrentMythicItemMatcher.load(current.keySet(), id -> current.get(id).get());
    }

    @Test
    void identifiesAnItemByNameAndLoreIgnoringColours() {
        CurrentMythicItemMatcher matcher = matcher();
        assertEquals("Sword_A", matcher.match(item(Material.DIAMOND_SWORD, "§b§l聖剣", List.of("§7Lv1", "§6攻撃力 10"), 5)));
        assertEquals("Sword_A", matcher.match(item(Material.DIAMOND_SWORD, "§f聖剣", List.of("Lv1", "§r攻撃力 10"), null)));
    }

    @Test
    void rejectsLookAlikesAndAmbiguousOrRandomDefinitions() {
        CurrentMythicItemMatcher matcher = matcher();
        // other lore (e.g. Lv2), material or model
        assertNull(matcher.match(item(Material.DIAMOND_SWORD, "§b§l聖剣", List.of("§7Lv2", "§6攻撃力 10"), 5)));
        assertNull(matcher.match(item(Material.IRON_SWORD, "§b§l聖剣", List.of("§7Lv1", "§6攻撃力 10"), 5)));
        assertNull(matcher.match(item(Material.DIAMOND_SWORD, "§b§l聖剣", List.of("§7Lv1", "§6攻撃力 10"), 6)));
        // two definitions generate the same item
        assertNull(matcher.match(item(Material.PAPER, "§aチケット", List.of("§7交換用"), null)));
        // a definition that generates another item every time
        assertNull(matcher.match(item(Material.STICK, "§eくじ", List.of("§71"), null)));
        // no name and lore
        assertNull(matcher.match(new ItemStack(Material.STONE)));
    }

    @Test
    void plainLoreIgnoresColoursButNotText() {
        List<String> current = CurrentMythicItemMatcher.plainLore(item(Material.DIAMOND_SWORD, "§b聖剣", List.of("§7Lv1", "§6攻撃力 10"), null));
        assertEquals(current, CurrentMythicItemMatcher.plainLore(item(Material.DIAMOND_SWORD, null, List.of("Lv1", "§r攻撃力 10 "), null)));
        assertNotEquals(current, CurrentMythicItemMatcher.plainLore(item(Material.DIAMOND_SWORD, null, List.of("§7Lv2", "§6攻撃力 10"), null)));
        assertNotEquals(current, CurrentMythicItemMatcher.plainLore(item(Material.DIAMOND_SWORD, null, List.of("§7Lv1"), null)));
        assertNotEquals(current, CurrentMythicItemMatcher.plainLore(item(Material.DIAMOND_SWORD, null, null, null)));
    }

    @Test
    void excludedIdsSupportExactAndPrefixMatches() {
        List<String> previous = Config.mythicItemExcludedIds;
        try {
            Config.mythicItemExcludedIds = List.of("SomeItem", "FFGGR_Under_*");
            assertTrue(Config.isMythicItemExcluded("someitem"));
            assertTrue(Config.isMythicItemExcluded("FFGGR_Under_Sword"));
            assertFalse(Config.isMythicItemExcluded("SomeItem2"));
            assertFalse(Config.isMythicItemExcluded("FFGGR_Over"));
        } finally {
            Config.mythicItemExcludedIds = previous;
        }
    }
}
