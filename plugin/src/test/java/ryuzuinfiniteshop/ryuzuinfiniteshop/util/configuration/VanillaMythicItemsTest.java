package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VanillaMythicItemsTest {
    @Test
    void splitsComponentStringAtTopLevel() {
        Map<String, String> components = VanillaMythicItems.components(
                "[minecraft:custom_data={MYTHIC_TYPE:\"a,b\",x:{y:[1,2]}},!minecraft:food,minecraft:damage=3]");
        assertEquals(List.of("minecraft:custom_data", "!minecraft:food", "minecraft:damage"),
                List.copyOf(components.keySet()));
        assertEquals("{MYTHIC_TYPE:\"a,b\",x:{y:[1,2]}}", components.get("minecraft:custom_data"));
        assertEquals("3", components.get("minecraft:damage"));
        assertTrue(VanillaMythicItems.components("[]").isEmpty());
        assertNull(VanillaMythicItems.components("nope"));
    }

    @Test
    void readsCompoundKeys() {
        assertEquals(Set.of("MYTHIC_TYPE", "PreventAnvil", "a b"),
                VanillaMythicItems.compoundKeys("{MYTHIC_TYPE:\"x\",PreventAnvil:1b,\"a b\":{c:\"}\"}}"));
        assertTrue(VanillaMythicItems.compoundKeys("{}").isEmpty());
        assertNull(VanillaMythicItems.compoundKeys("{novalue}"));
    }
}
