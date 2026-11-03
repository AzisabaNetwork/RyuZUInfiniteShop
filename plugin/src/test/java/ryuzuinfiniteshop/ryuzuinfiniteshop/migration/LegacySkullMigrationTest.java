package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacySkullMigrationTest {
    private static final String UUID_STR = "11111111-2222-3333-4444-555555555555";

    /** SkullOwner{Id:"..",Name:"Steve",Properties:{textures:[{Value:"VALUE"}]}} as 1.15.2 stored it. */
    private static String legacyInternal(String extraKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("SkullOwner");
            out.writeByte(8);
            out.writeUTF("Id");
            out.writeUTF(UUID_STR);
            out.writeByte(8);
            out.writeUTF("Name");
            out.writeUTF("Steve");
            out.writeByte(10);
            out.writeUTF("Properties");
            out.writeByte(9);
            out.writeUTF("textures");
            out.writeByte(10);
            out.writeInt(1);
            out.writeByte(8);
            out.writeUTF("Value");
            out.writeUTF("VALUE");
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            if (extraKey != null) {
                out.writeByte(8);
                out.writeUTF(extraKey);
                out.writeUTF("x");
            }
            out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String yamlWith(String internal) {
        return "data-version: 2\n"
                + "item:\n"
                + "  ==: org.bukkit.inventory.ItemStack\n"
                + "  v: 2230\n"
                + "  type: PLAYER_HEAD\n"
                + "  meta:\n"
                + "    ==: ItemMeta\n"
                + "    meta-type: SKULL\n"
                + "    skull-owner: Steve\n"
                + "    internal: " + internal + "\n";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> meta(String fixed) {
        Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(fixed);
        return (Map<String, Object>) ((Map<String, Object>) root.get("item")).get("meta");
    }

    @Test
    void convertsLegacyInternalToPlayerProfile() throws Exception {
        String fixed = LegacySkullMigration.fixRawYaml(yamlWith(legacyInternal(null)));
        Map<String, Object> meta = meta(fixed);

        Map<?, ?> profile = (Map<?, ?>) meta.get("skull-owner");
        assertEquals("PlayerProfile", profile.get("=="));
        assertEquals(UUID_STR, profile.get("uuid"));
        assertEquals("Steve", profile.get("name"));
        Map<?, ?> property = (Map<?, ?>) ((java.util.List<?>) profile.get("properties")).get(0);
        assertEquals("textures", property.get("name"));
        assertEquals("VALUE", property.get("value"));
        assertFalse(meta.containsKey("internal"));
    }

    @Test
    void keepsInternalWhenItHoldsOtherTags() throws Exception {
        String fixed = LegacySkullMigration.fixRawYaml(yamlWith(legacyInternal("Other")));
        assertTrue(meta(fixed).containsKey("internal"));
    }

    @Test
    void leavesUnrelatedYamlUntouched() {
        String text = "data-version: 2\nname: shop\n";
        assertSame(text, LegacySkullMigration.fixRawYaml(text));
    }
}
