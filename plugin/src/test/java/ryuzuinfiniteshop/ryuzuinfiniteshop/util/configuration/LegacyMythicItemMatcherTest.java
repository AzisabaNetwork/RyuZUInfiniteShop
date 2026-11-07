package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMythicItemMatcherTest {
    // Taken from the 1.15.2 MythicMobs Items/Other/User_Dice_item.yml
    private static final String DEFINITIONS = """
            プレイヤー用ダイス_10d:
              Id: 397
              Data: 3
              Options:
                SkinTexture: eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk3OTU1NDYyZTRlNTc2NjY0NDk5YWM0YTFjNTcyZjYxNDNmMTlhZDJkNjE5NDc3NjE5OGY4ZDEzNmZkYjIifX19
              Display: '&b&lDice&7[&3&l10d&7]'
              Lore:
              - '&6もとはお都市玉の時に使われたダイスをユーザー用に作り替えたもの'
              - '&6このダイスは10面あるらしい。'
              - '&6わんちゃん10面なら現実にもあるかもしれない'
              - '&4&lこのアイテムは全チャットに回した人/値が出ます'
              - '&4&l無意味に使いまわし、チャットを荒らした場合処罰をする可能性があります。'
            プレイヤー用ダイス_13d:
              Id: 397
              Data: 3
              Options:
                SkinTexture: eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk3OTU1NDYyZTRlNTc2NjY0NDk5YWM0YTFjNTcyZjYxNDNmMTlhZDJkNjE5NDc3NjE5OGY4ZDEzNmZkYjIifX19
              Display: '&b&lDice&7[&3&l13d&7]'
              Lore:
              - '&6このダイスは13面あるらしい。'
            removed_item:
              Id: 264
              Display: '&aGone'
            """;

    @TempDir
    Path directory;

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    private LegacyMythicItemMatcher load() throws IOException {
        File items = directory.resolve("Items/Other").toFile();
        assertTrue(items.mkdirs());
        Files.writeString(new File(items, "User_Dice_item.yml").toPath(), DEFINITIONS, StandardCharsets.UTF_8);
        Set<String> current = Set.of("プレイヤー用ダイス_10d", "プレイヤー用ダイス_13d");
        return LegacyMythicItemMatcher.load(directory.toFile(), id -> current.contains(id) ? new ItemStack(Material.PLAYER_HEAD) : null);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 5);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void identifiesAHeadThatLostItsSkinAndMythicTag() throws IOException {
        ItemStack saved = item(Material.PLAYER_HEAD, "§b§lDice§7[§3§l10d§7]", List.of(
                "§6もとはお都市玉の時に使われたダイスをユーザー用に作り替えたもの",
                "§6このダイスは10面あるらしい。",
                "§6わんちゃん10面なら現実にもあるかもしれない",
                "§4§lこのアイテムは全チャットに回した人/値が出ます",
                "§4§l無意味に使いまわし、チャットを荒らした場合処罰をする可能性があります。"));

        assertEquals("プレイヤー用ダイス_10d", load().match(saved));
    }

    @Test
    void ignoresColourDifferencesFromTheUpgradedComponents() throws IOException {
        ItemStack saved = item(Material.PLAYER_HEAD, "§bDice§7[§310d§7]", List.of(
                "§6もとはお都市玉の時に使われたダイスをユーザー用に作り替えたもの",
                "§6このダイスは10面あるらしい。",
                "§6わんちゃん10面なら現実にもあるかもしれない",
                "§4このアイテムは全チャットに回した人/値が出ます",
                "§4無意味に使いまわし、チャットを荒らした場合処罰をする可能性があります。"));

        assertEquals("プレイヤー用ダイス_10d", load().match(saved));
    }

    @Test
    void doesNotMatchOtherItemsOrIdsMissingInCurrentMythicMobs() throws IOException {
        LegacyMythicItemMatcher matcher = load();
        assertNull(matcher.match(item(Material.PLAYER_HEAD, "§b§lDice§7[§3§l10d§7]", List.of("§6別のLore"))));
        assertNull(matcher.match(item(Material.STONE, "§b§lDice§7[§3§l10d§7]", List.of("§6このダイスは13面あるらしい。"))));
        assertNull(matcher.match(item(Material.DIAMOND, "§aGone", List.of())));
    }
}
