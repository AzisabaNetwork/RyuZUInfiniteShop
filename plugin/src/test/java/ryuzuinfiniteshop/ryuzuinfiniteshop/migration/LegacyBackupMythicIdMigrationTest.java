package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBackupMythicIdMigrationTest {
    private static final String NAME = "§b§lDice§7[§3§l100d§7]";
    private static final List<String> LORE = List.of("§6このダイスは100面あるらしい。", "§4§lこのアイテムは全チャットに回した人/値が出ます");

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

    private static String internal(String mythicType) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(8);
            out.writeUTF("MYTHIC_TYPE");
            out.writeUTF(mythicType);
            out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String legacyItem(String type, String name, String mythicType) throws IOException {
        return """
                  - ==: org.bukkit.inventory.ItemStack
                    v: 2230
                    type: %s
                    meta:
                      ==: ItemMeta
                      meta-type: SKULL
                      display-name: '%s'
                      lore:
                      - '%s'
                      - '%s'
                      internal: %s
                """.formatted(type, name, LORE.get(0), LORE.get(1), internal(mythicType));
    }

    private static ItemStack head(String name, int amount) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(LORE);
        item.setItemMeta(meta);
        return item;
    }

    private void writeBackup(String text) throws IOException {
        File dir = shopsDir.resolve("backup/20260913-154556").toFile();
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, "pve,529,36,563.yml").toPath(), text, StandardCharsets.UTF_8);
        // A later backup in the 1.21 format must not hide the 1.15.2 one.
        File later = shopsDir.resolve("backup/20261105-190025").toFile();
        assertTrue(later.mkdirs());
        Files.writeString(new File(later, "pve,529,36,563.yml").toPath(), "Trades: []\n", StandardCharsets.UTF_8);
    }

    @Test
    void restoresTheIdOfTheItemAtTheSamePositionInTheBackup() throws IOException {
        writeBackup("Trades:\n- give:\n" + legacyItem("PLAYER_HEAD", NAME, "プレイヤー用ダイス_100d")
                + "  take:\n" + legacyItem("PLAYER_HEAD", NAME, "kirisio_SuperFake100"));
        YamlConfiguration yaml = new YamlConfiguration();
        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("give", List.of(head(NAME, 3)));
        trade.put("take", List.of(head(NAME, 1)));
        yaml.set("Trades", List.of(trade));

        assertEquals(2, LegacyBackupMythicIdMigration.restore(yaml, shopsDir.toFile(), "pve,529,36,563.yml"));

        Map<?, ?> restored = (Map<?, ?>) yaml.getList("Trades").get(0);
        assertEquals(new MythicItem("プレイヤー用ダイス_100d", 3), ((List<?>) restored.get("give")).get(0));
        assertEquals(new MythicItem("kirisio_SuperFake100", 1), ((List<?>) restored.get("take")).get(0));
    }

    @Test
    void leavesItemsThatNoLongerMatchTheBackupAlone() throws IOException {
        writeBackup("Trades:\n- give:\n" + legacyItem("PLAYER_HEAD", "§b§lDice§7[§3§l10d§7]", "プレイヤー用ダイス_10d"));
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack replaced = head(NAME, 1);
        yaml.set("Trades", List.of(Map.of("give", List.of(replaced))));

        assertEquals(0, LegacyBackupMythicIdMigration.restore(yaml, shopsDir.toFile(), "pve,529,36,563.yml"));
        Map<?, ?> trade = (Map<?, ?>) yaml.getList("Trades").get(0);
        assertEquals(replaced, ((List<?>) trade.get("give")).get(0));
    }

    @Test
    void doesNothingWithoutABackup() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Trades", List.of(Map.of("give", List.of(head(NAME, 1)))));
        assertEquals(0, LegacyBackupMythicIdMigration.restore(yaml, shopsDir.toFile(), "pve,529,36,563.yml"));
    }
}
