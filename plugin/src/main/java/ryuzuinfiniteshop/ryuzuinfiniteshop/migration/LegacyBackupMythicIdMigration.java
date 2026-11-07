package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LegacyMythicItemMatcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Restores the MythicMobs ID of shop items that lost it on the way from 1.15.2, using the 1.15.2 copy of the same
 * shop file kept in {@code shops/backup/<timestamp>/}.
 * <p>
 * Items cannot always be told apart by their look (e.g. a player dice and the rigged dice share name, lore and skin),
 * so the ID is taken from the item at the same position in the backup, whose base64 "internal" NBT still holds
 * {@code MYTHIC_TYPE}. It is only applied when that backup item has the same name and lore, so trades edited since
 * are left alone.
 */
public final class LegacyBackupMythicIdMigration implements MigrationStep {
    public static final int FROM_VERSION = 5;
    public static final int TO_VERSION = 6;

    @Override
    public int fromVersion() {
        return FROM_VERSION;
    }

    @Override
    public int toVersion() {
        return TO_VERSION;
    }

    @Override
    public String description() {
        return "Restore MythicMobs IDs lost since 1.15.2 from the shop's 1.15.2 backup";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The restore needs the shop file's backups (see ShopMigrationService); only the version is bumped here.
        yaml.set("data-version", TO_VERSION);
    }

    /**
     * @return how many items got their ID back
     */
    public static int restore(YamlConfiguration yaml, File shopsDir, String fileName) {
        Object legacy = findLegacyBackup(shopsDir, fileName);
        if (!(legacy instanceof Map<?, ?> legacyRoot)) return 0;
        int[] count = {0};
        for (String key : yaml.getKeys(false)) {
            Object current = yaml.get(key);
            Object restored = restore(current, legacyRoot.get(key), count);
            if (restored != current) yaml.set(key, restored);
        }
        return count[0];
    }

    /** The oldest backup of the file that is still in the 1.15.2 format, parsed without Bukkit. */
    static Object findLegacyBackup(File shopsDir, String fileName) {
        File[] dirs = new File(shopsDir, "backup").listFiles(File::isDirectory);
        if (dirs == null) return null;
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        for (File dir : dirs) {
            File backup = new File(dir, fileName);
            if (!backup.isFile()) continue;
            try {
                String text = Files.readString(backup.toPath(), StandardCharsets.UTF_8);
                if (!text.contains("internal:")) continue;
                return new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
            } catch (IOException | RuntimeException ignored) {
                // try the next backup
            }
        }
        return null;
    }

    static Object restore(Object current, Object legacy, int[] count) {
        if (legacy == null) return current;
        if (current instanceof ItemStack item) {
            if (!(legacy instanceof Map<?, ?> legacyItem)) return current;
            String id = legacyMythicType(legacyItem);
            if (id == null || !sameLook(item, legacyItem)) return current;
            count[0]++;
            return new MythicItem(id, item.getAmount());
        }
        if (current instanceof ConfigurationSection section && legacy instanceof Map<?, ?> legacyMap) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                Object restored = restore(value, legacyMap.get(key), count);
                if (restored != value) section.set(key, restored);
            }
            return current;
        }
        if (current instanceof Map<?, ?> map && legacy instanceof Map<?, ?> legacyMap) {
            Map<Object, Object> result = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object restored = restore(entry.getValue(), legacyMap.get(entry.getKey()), count);
                changed |= restored != entry.getValue();
                result.put(entry.getKey(), restored);
            }
            return changed ? result : current;
        }
        if (current instanceof List<?> list && legacy instanceof List<?> legacyList) {
            List<Object> result = new ArrayList<>(list.size());
            boolean changed = false;
            for (int i = 0; i < list.size(); i++) {
                Object value = list.get(i);
                Object restored = i < legacyList.size() ? restore(value, legacyList.get(i), count) : value;
                changed |= restored != value;
                result.add(restored);
            }
            return changed ? result : current;
        }
        return current;
    }

    private static String legacyMythicType(Map<?, ?> legacyItem) {
        if (!(legacyItem.get("meta") instanceof Map<?, ?> meta) || !(meta.get("internal") instanceof String internal))
            return null;
        try {
            Map<String, Object> nbt = LegacySkullMigration.readNbt(Base64.getDecoder().decode(internal.replaceAll("\\s", "")));
            return nbt.get("MYTHIC_TYPE") instanceof String id && !id.isBlank() ? id : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean sameLook(ItemStack item, Map<?, ?> legacyItem) {
        Map<?, ?> legacyMeta = (Map<?, ?>) legacyItem.get("meta");
        ItemMeta meta = item.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? LegacyMythicItemMatcher.readableText(meta.getDisplayName()) : null;
        if (!Objects.equals(plain(name), plain(legacyMeta.get("display-name")))) return false;
        List<String> lore = new ArrayList<>();
        if (meta != null && meta.hasLore() && meta.getLore() != null)
            for (String line : meta.getLore()) lore.add(plain(LegacyMythicItemMatcher.readableText(line)));
        List<String> legacyLore = new ArrayList<>();
        if (legacyMeta.get("lore") instanceof List<?> lines)
            for (Object line : lines) legacyLore.add(plain(line));
        if (!lore.equals(legacyLore)) return false;
        // Without a name or lore, the material is the only thing left to compare.
        return name != null || !lore.isEmpty() || item.getType().name().equals(String.valueOf(legacyItem.get("type")));
    }

    private static String plain(Object text) {
        if (text == null) return null;
        String stripped = ChatColor.stripColor(String.valueOf(text)).trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
