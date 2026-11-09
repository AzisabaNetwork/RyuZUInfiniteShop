package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyContainerMigrationTest {
    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    /** The 1.15.2 backup of the real shop whose shulker box prize turned into a gold ingot. */
    private static String realShop() throws IOException {
        try (InputStream in = LegacyContainerMigrationTest.class.getResourceAsStream("/legacy/shulker-shop-1.15.2.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> legacyShulker() throws IOException {
        Map<?, ?> root = (Map<?, ?>) LegacyContainerMigration.parseLegacy(realShop());
        assertNotNull(root);
        Map<?, ?> trade = ((List<Map<?, ?>>) root.get("Trades")).get(0);
        return ((List<Map<?, ?>>) trade.get("give")).get(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsDataFixerInputFromRealShulker() throws IOException {
        Map<String, Object> nbt = LegacyContainerMigration.legacyItemNbt(legacyShulker());
        assertNotNull(nbt);
        assertEquals("minecraft:yellow_shulker_box", nbt.get("id"));
        assertEquals((byte) 1, nbt.get("Count"));
        assertEquals(2230, nbt.get("DataVersion"));
        List<?> contents = LegacyContainerMigration.legacyContents(nbt);
        assertNotNull(contents);
        assertEquals(27, contents.size());
        Map<String, Object> display = (Map<String, Object>) ((Map<String, Object>) nbt.get("tag")).get("display");
        assertTrue(((String) display.get("Name")).contains("ゲリラ"));
        // the result must be writable for deserializeBytes
        assertTrue(LegacySkullMigration.writeNbt(nbt).length > 0);
        // no 1.15.2 camelCase attribute name may be left anywhere in the box
        assertFalse(LegacySkullMigration.readNbt(LegacySkullMigration.writeNbt(nbt)).toString().matches("(?s).*AttributeName=generic[.][a-z]+[A-Z].*"));
    }

    @Test
    void detectsTheBrokenItem() throws IOException {
        Map<?, ?> legacy = legacyShulker();
        List<?> contents = LegacyContainerMigration.legacyContents(LegacyContainerMigration.legacyItemNbt(legacy));
        // what the current server made of it: the first item of the box
        assertTrue(LegacyContainerMigration.looksBroken(new ItemStack(Material.GOLD_INGOT), legacy, contents));
        // an empty box of the same kind
        assertTrue(LegacyContainerMigration.looksBroken(new ItemStack(Material.YELLOW_SHULKER_BOX), legacy, contents));
        // something else the shop owner put there since
        assertFalse(LegacyContainerMigration.looksBroken(new ItemStack(Material.DIAMOND), legacy, contents));
    }

    @Test
    void attributeKey() {
        assertEquals("minecraft:generic.attack_damage", LegacyContainerMigration.legacyAttributeKey("generic.attackDamage"));
    }
}
