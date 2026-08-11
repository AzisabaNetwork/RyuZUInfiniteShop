package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MythicMobsItemMigrationTest {

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    void regeneratesNestedItemStacksAndPreservesTheirAmount() {
        ItemStack oldItem = new ItemStack(Material.DIAMOND, 7);
        AtomicInteger regenerated = new AtomicInteger();
        Object result = MythicMobsItemMigration.regenerateSavedItems(
                List.of(Map.of("give", List.of(oldItem))),
                item -> {
                    regenerated.incrementAndGet();
                    assertEquals(7, item.getAmount());
                    return new ItemStack(Material.NETHERITE_INGOT, item.getAmount());
                }
        );

        List<?> entries = assertInstanceOf(List.class, result);
        Map<?, ?> trade = assertInstanceOf(Map.class, entries.get(0));
        List<?> give = assertInstanceOf(List.class, trade.get("give"));
        ItemStack updated = assertInstanceOf(ItemStack.class, give.get(0));
        assertEquals(Material.NETHERITE_INGOT, updated.getType());
        assertEquals(7, updated.getAmount());
        assertEquals(1, regenerated.get());
    }

    @Test
    void preservesItemsTheCurrentMythicMobsConfigurationCannotRegenerate() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        Object result = MythicMobsItemMigration.regenerateSavedItems(item, ignored -> null);

        assertSame(item, result);
    }
}
