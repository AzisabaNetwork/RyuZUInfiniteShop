package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyLeatherColorMigrationTest {
    private static final String FILE = "lobby1,762,42,1805.yml";
    private static final String LEGACY = """
            Npc:
              Options:
                Equipments:
                - ==: org.bukkit.inventory.ItemStack
                  v: 2230
                  type: AIR
                - ==: org.bukkit.inventory.ItemStack
                  v: 2230
                  type: LEATHER_LEGGINGS
                  meta:
                    ==: ItemMeta
                    meta-type: LEATHER_ARMOR
                    color:
                      ==: Color
                      RED: 249
                      BLUE: 29
                      GREEN: 128
                - ==: org.bukkit.inventory.ItemStack
                  v: 2230
                  type: LEATHER_HORSE_ARMOR
                  meta:
                    ==: ItemMeta
                    meta-type: LEATHER_ARMOR
                    color:
                      ==: Color
                      RED: 1
                      BLUE: 2
                      GREEN: 3
            """;

    @TempDir
    Path shopsDir;

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    void rewritesTheMetaTypeOfLeatherArmorPieces() {
        String fixed = LegacyLeatherColorMigration.fixRawYaml(LEGACY);
        assertTrue(fixed.contains("meta-type: COLORABLE_ARMOR"));
        // Horse armor still uses the LEATHER_ARMOR meta.
        assertTrue(fixed.contains("meta-type: LEATHER_ARMOR"));
        assertTrue(fixed.contains("RED: 249"));
    }

    @Test
    void leavesTextWithoutLegacyLeatherArmorUntouched() {
        String text = "Trades: []\n";
        assertSame(text, LegacyLeatherColorMigration.fixRawYaml(text));
    }

    @Test
    void restoresTheColorLostInAnEarlierMigrationFromTheBackup() throws IOException {
        File dir = shopsDir.resolve("backup/20261105-190025").toFile();
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, FILE).toPath(), LEGACY, StandardCharsets.UTF_8);

        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        yaml.set("Npc.Options.Equipments", List.of(new ItemStack(Material.AIR), leggings));

        assertEquals(1, LegacyLeatherColorMigration.restore(yaml, shopsDir.toFile(), FILE));
        ItemStack restored = (ItemStack) yaml.getList("Npc.Options.Equipments").get(1);
        assertEquals(Color.fromRGB(249, 128, 29), ((LeatherArmorMeta) restored.getItemMeta()).getColor());
    }

    @Test
    void keepsArmorDyedSinceTheBackup() throws IOException {
        File dir = shopsDir.resolve("backup/20261105-190025").toFile();
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, FILE).toPath(), LEGACY, StandardCharsets.UTF_8);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta meta = (LeatherArmorMeta) leggings.getItemMeta();
        meta.setColor(Color.BLUE);
        leggings.setItemMeta(meta);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.Equipments", List.of(new ItemStack(Material.AIR), leggings));

        assertEquals(0, LegacyLeatherColorMigration.restore(yaml, shopsDir.toFile(), FILE));
        ItemStack kept = (ItemStack) yaml.getList("Npc.Options.Equipments").get(1);
        assertEquals(Color.BLUE, ((LeatherArmorMeta) kept.getItemMeta()).getColor());
    }
}
