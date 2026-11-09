package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ShieldMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyShieldColorMigrationTest {
    private static final String FILE = "shop,-107,4,212.yml";
    /** {BlockEntityTag:{id:"minecraft:banner",Base:13,Patterns:[{cs,0},{ts,4},{tts,14}]},CUSTOM:"keep"} */
    private static final String PATTERNED_INTERNAL =
            "H4sIAAO/8WoC/1XLwQrCMBCE4aGJGgMWn8dji0dRsD7AGlcJrRtJ9uLba8FDnNvPx3jAo+2mFMa9aNT3QA+HJt6wfUbhkOmuuyuJcDawHRUGsFnDnUiVsxT/beOw+jWaUAwWfZpSxryatCZbk9E/a+ffsr+ch+MBdmR+4QNArX8dqQAAAA==";
    /** {BlockEntityTag:{Base:15}}, taken from a real 1.15.2 shop. */
    private static final String BLACK_INTERNAL =
            "H4sIAAAAAAAA/+NiYOBi4HPKyU/Ods0rySypDElMZ2ZgcUosTmVgYOBnYAAAyfMZgCEAAAA=";
    private static final String LEGACY = """
            Trades:
            - give:
              - ==: org.bukkit.inventory.ItemStack
                v: 2230
                type: SHIELD
                meta:
                  ==: ItemMeta
                  meta-type: TILE_ENTITY
                  display-name: gift
                  internal: %s
                  blockMaterial: SHIELD
              - ==: org.bukkit.inventory.ItemStack
                v: 2230
                type: SHIELD
                meta:
                  ==: ItemMeta
                  meta-type: TILE_ENTITY
                  internal: %s
                  blockMaterial: SHIELD
            """.formatted(PATTERNED_INTERNAL, BLACK_INTERNAL);

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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(String text) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        return (List<Map<String, Object>>) ((List<Map<String, Object>>) root.get("Trades")).get(0).get("give");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rewritesLegacyShieldsIntoShieldMeta() throws IOException {
        List<Map<String, Object>> items = items(LegacyShieldColorMigration.fixRawYaml(LEGACY));

        Map<String, Object> gift = (Map<String, Object>) items.get(0).get("meta");
        assertEquals("SHIELD", gift.get("meta-type"));
        assertEquals("GREEN", gift.get("base-color"));
        assertEquals("gift", gift.get("display-name"));
        assertFalse(gift.containsKey("blockMaterial"));
        assertEquals(List.of(
                Map.of("==", "Pattern", "color", "WHITE", "pattern", "minecraft:stripe_center"),
                Map.of("==", "Pattern", "color", "YELLOW", "pattern", "minecraft:stripe_top"),
                Map.of("==", "Pattern", "color", "RED", "pattern", "minecraft:triangles_top")), gift.get("patterns"));
        // Other legacy tags stay in "internal", only the block entity data is moved out.
        Map<String, Object> rest = LegacySkullMigration.readNbt(Base64.getDecoder().decode((String) gift.get("internal")));
        assertEquals(Map.of("CUSTOM", "keep"), rest);

        Map<String, Object> black = (Map<String, Object>) items.get(1).get("meta");
        assertEquals("BLACK", black.get("base-color"));
        assertFalse(black.containsKey("patterns"));
        assertFalse(black.containsKey("internal"));
    }

    @Test
    void rewrittenShieldsDeserializeWithTheirColors() throws Exception {
        // MockBukkit's ShieldMetaMock rejects deserialized Pattern objects (Paper requires them), so only the
        // pattern-less shield is checked here; the pattern format is the one Paper's own CraftMetaShield writes.
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(LegacyShieldColorMigration.fixRawYaml(LEGACY));
        List<?> give = (List<?>) ((Map<?, ?>) yaml.getMapList("Trades").get(0)).get("give");
        assertEquals(DyeColor.BLACK, ((ShieldMeta) ((ItemStack) give.get(1)).getItemMeta()).getBaseColor());
    }

    @Test
    void leavesTextWithoutLegacyShieldsUntouched() {
        String text = "Trades: []\n";
        assertSame(text, LegacyShieldColorMigration.fixRawYaml(text));
    }

    @Test
    void restoresTheColorsLostInAnEarlierMigrationFromTheBackup() throws IOException {
        writeBackup();
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack white = new ItemStack(Material.SHIELD);
        ShieldMeta meta = (ShieldMeta) white.getItemMeta();
        meta.setBaseColor(DyeColor.WHITE);
        white.setItemMeta(meta);
        yaml.set("Trades", List.of(Map.of("give", List.of(white, new ItemStack(Material.SHIELD)))));

        assertEquals(2, LegacyShieldColorMigration.restore(yaml, shopsDir.toFile(), FILE));
        List<?> give = (List<?>) ((Map<?, ?>) yaml.getList("Trades").get(0)).get("give");
        ShieldMeta gift = (ShieldMeta) ((ItemStack) give.get(0)).getItemMeta();
        assertEquals(DyeColor.GREEN, gift.getBaseColor());
        assertEquals(3, gift.numberOfPatterns());
        assertEquals(DyeColor.BLACK, ((ShieldMeta) ((ItemStack) give.get(1)).getItemMeta()).getBaseColor());
    }

    @Test
    void keepsShieldsRecoloredSinceTheBackup() throws IOException {
        writeBackup();
        ItemStack shield = new ItemStack(Material.SHIELD);
        ShieldMeta meta = (ShieldMeta) shield.getItemMeta();
        meta.setBaseColor(DyeColor.BLUE);
        shield.setItemMeta(meta);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Trades", List.of(Map.of("give", List.of(shield))));

        assertEquals(0, LegacyShieldColorMigration.restore(yaml, shopsDir.toFile(), FILE));
    }

    private void writeBackup() throws IOException {
        File dir = shopsDir.resolve("backup/20261105-190025").toFile();
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, FILE).toPath(), LEGACY, StandardCharsets.UTF_8);
    }
}
