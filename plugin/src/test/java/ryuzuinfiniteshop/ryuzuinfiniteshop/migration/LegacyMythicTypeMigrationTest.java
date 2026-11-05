package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMythicTypeMigrationTest {
    // {MYTHIC_TYPE:"sigenpickaxe10SAC"} taken from a real 1.15.2 shop file
    private static final String INTERNAL = "H4sIAAAAAAAA/+NiYOBg4PaNDPHwdI4PiQxwZRAszkxPzSvITM5OrEg1NAh2dGYAAIefIIUlAAAA";

    @Test
    void replacesMythicItemStackWithReference() {
        String yaml = "data-version: 2\nTrades:\n- give:\n  - ==: org.bukkit.inventory.ItemStack\n    v: 2230\n    type: DIAMOND_PICKAXE\n    amount: 3\n"
                + "    meta:\n      ==: ItemMeta\n      meta-type: UNSPECIFIC\n      internal: " + INTERNAL + "\n";
        Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(LegacyMythicTypeMigration.fixRawYaml(yaml));
        Map<?, ?> trade = (Map<?, ?>) ((List<?>) root.get("Trades")).get(0);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) trade.get("give")).get(0);
        assertEquals(LegacyMythicTypeMigration.MYTHIC_ITEM_CLASS, item.get("=="));
        assertEquals(3, item.get("amount"));
        assertNotNull(item.get("id"));
    }

    @Test
    void leavesPlainItemsUntouched() {
        String yaml = "data-version: 2\nitem:\n  ==: org.bukkit.inventory.ItemStack\n  type: DIRT\n";
        assertSame(yaml, LegacyMythicTypeMigration.fixRawYaml(yaml));
    }
}
