package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MythicMobsItemMigration implements MigrationStep {
    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
    }

    @Override
    public String description() {
        return "Regenerate saved MythicMobs v4 item stacks with MythicMobs v5";
    }

    @Override
    public void migrate(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (!MythicInstanceProvider.isLoaded()) return;

        boolean changed = false;
        for (String key : yaml.getKeys(false)) {
            Conversion conversion = convert(yaml.get(key), item -> regenerateSafely(item, this::regenerate));
            if (conversion.changed()) {
                yaml.set(key, conversion.value());
                changed = true;
            }
        }
        if (changed || MythicInstanceProvider.isLoaded()) {
            yaml.set("data-version", toVersion());
        }
    }

    private static Conversion convert(Object value, Function<ItemStack, ItemStack> regenerator) {
        if (value instanceof ItemStack item) {
            ItemStack updated = regenerator.apply(item);
            return updated == null ? new Conversion(value, false) : new Conversion(updated, true);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object entry : list) {
                Conversion conversion = convert(entry, regenerator);
                converted.add(conversion.value());
                changed |= conversion.changed();
            }
            return new Conversion(changed ? converted : value, changed);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> converted = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Conversion conversion = convert(entry.getValue(), regenerator);
                converted.put(entry.getKey(), conversion.value());
                changed |= conversion.changed();
            }
            return new Conversion(changed ? converted : value, changed);
        }
        return new Conversion(value, false);
    }

    private ItemStack regenerate(ItemStack item) {
        String id = MythicInstanceProvider.getInstance().getID(item);
        if (id == null || id.isBlank()) return null;
        try {
            return MythicInstanceProvider.getInstance().getMythicItem(id, item.getAmount());
        } catch (RuntimeException e) {
            throw new IllegalStateException("MythicMobs item '" + id + "': " + e.getMessage(), e);
        }
    }

    static Object regenerateSavedItems(Object value, Function<ItemStack, ItemStack> regenerator) {
        return convert(value, item -> regenerateSafely(item, regenerator)).value();
    }

    /**
     * Regenerates one item without letting a broken MythicMobs item config hurt the rest of the file:
     * <ul>
     *     <li>an exception (e.g. "Unknown attribute") keeps the saved item instead of failing the whole file;</li>
     *     <li>a player head whose config texture MythicMobs cannot apply (e.g. invalid base64) keeps the saved
     *     profile instead of turning into a blank head.</li>
     * </ul>
     */
    static ItemStack regenerateSafely(ItemStack item, Function<ItemStack, ItemStack> regenerator) {
        ItemStack updated;
        try {
            updated = regenerator.apply(item);
        } catch (RuntimeException e) {
            warn("Could not regenerate MythicMobs item, keeping the saved one: " + e);
            return null;
        }
        if (updated == null) return null;
        keepSavedSkullProfile(item, updated);
        return updated;
    }

    private static void keepSavedSkullProfile(ItemStack saved, ItemStack updated) {
        if (!saved.hasItemMeta() || !(saved.getItemMeta() instanceof SkullMeta savedMeta)) return;
        ItemMeta meta = updated.getItemMeta();
        if (!(meta instanceof SkullMeta updatedMeta)) return;
        PlayerProfile savedProfile = savedMeta.getOwnerProfile();
        if (!hasTextures(savedProfile) || hasTextures(updatedMeta.getOwnerProfile())) return;
        updatedMeta.setOwnerProfile(savedProfile);
        updated.setItemMeta(updatedMeta);
    }

    private static boolean hasTextures(PlayerProfile profile) {
        return profile != null && !profile.getTextures().isEmpty();
    }

    private static void warn(String message) {
        RyuZUInfiniteShop plugin = RyuZUInfiniteShop.getPlugin();
        if (plugin != null && plugin.getLogger() != null) plugin.getLogger().warning(message);
    }

    private record Conversion(Object value, boolean changed) {}
}
