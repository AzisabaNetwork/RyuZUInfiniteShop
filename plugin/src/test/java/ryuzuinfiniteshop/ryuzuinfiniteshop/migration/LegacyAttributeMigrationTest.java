package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAttributeMigrationTest {
    private static final String UUID_STR = "11111111-2222-3333-4444-555555555555";

    private static String yaml(String attributeKey, String slot) {
        return "data-version: 3\n"
                + "item:\n"
                + "  ==: org.bukkit.inventory.ItemStack\n"
                + "  type: DIAMOND_SWORD\n"
                + "  meta:\n"
                + "    ==: ItemMeta\n"
                + "    meta-type: UNSPECIFIC\n"
                + "    attribute-modifiers:\n"
                + "      " + attributeKey + ":\n"
                + "      - ==: org.bukkit.attribute.AttributeModifier\n"
                + "        uuid: " + UUID_STR + "\n"
                + "        name: " + attributeKey + "\n"
                + "        operation: 0\n"
                + "        amount: 7.0\n"
                + (slot == null ? "" : "        slot: " + slot + "\n");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> modifiers(String fixed) {
        Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(fixed);
        Map<String, Object> meta = (Map<String, Object>) ((Map<String, Object>) root.get("item")).get("meta");
        return (Map<String, Object>) meta.get("attribute-modifiers");
    }

    @Test
    void convertsNmsNameAndUuid() {
        Map<String, Object> mods = modifiers(LegacyAttributeMigration.fixRawYaml(yaml("generic.attackDamage", "HAND")));
        assertEquals(List.of("minecraft:attack_damage"), List.copyOf(mods.keySet()));
        Map<?, ?> mod = (Map<?, ?>) ((List<?>) mods.get("minecraft:attack_damage")).get(0);
        assertEquals("minecraft:" + UUID_STR, mod.get("key"));
        assertFalse(mod.containsKey("uuid"));
        assertEquals("mainhand", mod.get("slot"));
        assertEquals(7.0, ((Number) mod.get("amount")).doubleValue());
    }

    @Test
    void keepsModifierWithoutSlotOnAnySlot() {
        Map<String, Object> mods = modifiers(LegacyAttributeMigration.fixRawYaml(yaml("generic.maxHealth", null)));
        Map<?, ?> mod = (Map<?, ?>) ((List<?>) mods.get("minecraft:max_health")).get(0);
        assertFalse(mod.containsKey("slot"));
    }

    @Test
    void convertsAttributeKeys() {
        assertEquals("minecraft:attack_damage", LegacyAttributeMigration.convertAttributeKey("generic.attackDamage"));
        assertEquals("minecraft:attack_damage", LegacyAttributeMigration.convertAttributeKey("GENERIC_ATTACK_DAMAGE"));
        assertEquals("minecraft:attack_damage", LegacyAttributeMigration.convertAttributeKey("minecraft:attack_damage"));
        assertEquals("minecraft:armor_toughness", LegacyAttributeMigration.convertAttributeKey("generic.armorToughness"));
        assertEquals("minecraft:jump_strength", LegacyAttributeMigration.convertAttributeKey("horse.jumpStrength"));
        assertEquals("minecraft:spawn_reinforcements", LegacyAttributeMigration.convertAttributeKey("zombie.spawnReinforcements"));
    }

    @Test
    void isIdempotent() {
        String once = LegacyAttributeMigration.fixRawYaml(yaml("generic.attackDamage", "OFF_HAND"));
        assertSame(once, LegacyAttributeMigration.fixRawYaml(once));
    }

    @Test
    void leavesUnrelatedYamlUntouched() {
        String text = "data-version: 3\nname: shop\n";
        assertSame(text, LegacyAttributeMigration.fixRawYaml(text));
    }
}
