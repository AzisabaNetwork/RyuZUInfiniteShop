package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Material;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    // MockBukkit does not keep skull textures, so heads are modelled with mocks here.
    private static PlayerProfile profile(boolean textured) {
        PlayerProfile profile = mock(PlayerProfile.class);
        PlayerTextures textures = mock(PlayerTextures.class);
        when(textures.isEmpty()).thenReturn(!textured);
        when(profile.getTextures()).thenReturn(textures);
        return profile;
    }

    private static ItemStack head(SkullMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    // MythicMobs drops the texture when the config's SkinTexture is broken (e.g. bookD_flashhead's extra '=')
    @Test
    void keepsSavedHeadTextureWhenRegeneratedHeadHasNone() {
        PlayerProfile savedProfile = profile(true);
        SkullMeta savedMeta = mock(SkullMeta.class);
        when(savedMeta.getOwnerProfile()).thenReturn(savedProfile);
        SkullMeta blankMeta = mock(SkullMeta.class);
        ItemStack blank = head(blankMeta);

        Object result = MythicMobsItemMigration.regenerateSavedItems(head(savedMeta), ignored -> blank);

        assertSame(blank, result);
        verify(blankMeta).setOwnerProfile(savedProfile);
        verify(blank).setItemMeta(blankMeta);
    }

    @Test
    void prefersRegeneratedHeadTextureWhenItHasOne() {
        PlayerProfile savedProfile = profile(true);
        PlayerProfile freshProfile = profile(true);
        SkullMeta savedMeta = mock(SkullMeta.class);
        when(savedMeta.getOwnerProfile()).thenReturn(savedProfile);
        SkullMeta freshMeta = mock(SkullMeta.class);
        when(freshMeta.getOwnerProfile()).thenReturn(freshProfile);
        ItemStack fresh = head(freshMeta);

        Object result = MythicMobsItemMigration.regenerateSavedItems(head(savedMeta), ignored -> fresh);

        assertSame(fresh, result);
        verify(freshMeta, never()).setOwnerProfile(any());
    }

    // e.g. "Unknown attribute 'minecraft:attack'" from one item must not fail the whole shop file
    @Test
    void keepsSavedItemWhenRegenerationThrows() {
        ItemStack broken = new ItemStack(Material.DIAMOND);
        ItemStack fine = new ItemStack(Material.EMERALD);
        Object result = MythicMobsItemMigration.regenerateSavedItems(List.of(broken, fine), item -> {
            if (item.getType() == Material.DIAMOND) throw new IllegalArgumentException("Unknown attribute 'minecraft:attack'");
            return new ItemStack(Material.NETHERITE_INGOT);
        });
        List<?> items = assertInstanceOf(List.class, result);
        assertSame(broken, items.get(0));
        assertEquals(Material.NETHERITE_INGOT, ((ItemStack) items.get(1)).getType());
    }
}
