package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Keeps the contents of containers (shulker boxes...) saved by 1.15.2 builds.
 * <p>
 * 1.15.2 wrote such items with {@code meta-type: TILE_ENTITY}; their contents only lived in
 * {@code BlockEntityTag.Items} of the base64 "internal" NBT, in the 1.15.2 item format ({@code Count} byte,
 * {@code tag}). Current servers read that tag without running the data fixers, so the contents came back broken:
 * in the real shop {@code world,1830,92,739} the prize, a shulker box holding 27 items, turned into a single gold
 * ingot. Here the whole legacy item is rebuilt through Minecraft's DataFixer ({@code ItemStack.deserializeBytes}),
 * which upgrades the box and every item inside it, and replaces the broken item.
 * <p>
 * The legacy item is taken from the file itself when it has not been loaded by a current server yet, otherwise from
 * the shop's 1.15.2 backup. An item is only replaced when it still looks broken (same container with fewer items,
 * or the first item of the box in place of the box), so items edited since are left alone.
 * Needs a running Paper server; without {@code deserializeBytes} nothing is changed.
 */
public final class LegacyContainerMigration implements MigrationStep {
    public static final int FROM_VERSION = 8;
    public static final int TO_VERSION = 9;

    /** Present in the YAML of every 1.15.2 block entity item. */
    static final String MARKER = "blockMaterial:";
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final int LEGACY_DATA_VERSION = 2230; // 1.15.2

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
        return "Keep the contents of shulker boxes and other containers saved by 1.15.2";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The actual restoration needs the raw text or the backups (see ShopMigrationService); only the version is bumped here.
        yaml.set("data-version", TO_VERSION);
    }

    /** The raw YAML parsed without Bukkit, if it still contains 1.15.2 block entity items. */
    static Object parseLegacy(String rawText) {
        if (rawText == null || !rawText.contains(MARKER)) return null;
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(rawText);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * @param rawText the file's text before Bukkit loaded it; used when it still holds the 1.15.2 items
     * @return how many containers were restored
     */
    public static int restore(YamlConfiguration yaml, String rawText, File shopsDir, String fileName) {
        if (deserializeBytesMethod() == null) return 0;
        Object legacy = parseLegacy(rawText);
        if (legacy == null) legacy = LegacyBackupMythicIdMigration.findLegacyBackup(shopsDir, fileName, MARKER);
        if (!(legacy instanceof Map<?, ?> legacyRoot)) return 0;
        int[] count = {0};
        for (String key : yaml.getKeys(false)) {
            Object value = yaml.get(key);
            Object restored = restore(value, legacyRoot.get(key), count);
            if (restored != value) yaml.set(key, restored);
        }
        return count[0];
    }

    @SuppressWarnings("unchecked")
    static Object restore(Object current, Object legacy, int[] count) {
        if (legacy == null || current == null) return current;
        if (current instanceof ItemStack item) {
            if (!(legacy instanceof Map<?, ?> legacyItem)) return current;
            ItemStack rebuilt = rebuildIfBroken(item, legacyItem);
            if (rebuilt == null) return current;
            count[0]++;
            return rebuilt;
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
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
                Object restored = restore(entry.getValue(), legacyMap.get(entry.getKey()), count);
                if (restored != entry.getValue()) entry.setValue(restored);
            }
            return current;
        }
        if (current instanceof List<?> list && legacy instanceof List<?> legacyList) {
            List<Object> l = (List<Object>) list;
            for (int i = 0; i < Math.min(l.size(), legacyList.size()); i++) {
                Object restored = restore(l.get(i), legacyList.get(i), count);
                if (restored != l.get(i)) l.set(i, restored);
            }
        }
        return current;
    }

    private static ItemStack rebuildIfBroken(ItemStack current, Map<?, ?> legacyItem) {
        Map<String, Object> legacyNbt = legacyItemNbt(legacyItem);
        if (legacyNbt == null) return null;
        List<?> legacyContents = legacyContents(legacyNbt);
        if (legacyContents == null || legacyContents.isEmpty()) return null;
        if (!looksBroken(current, legacyItem, legacyContents)) return null;
        try {
            ItemStack rebuilt = (ItemStack) deserializeBytesMethod().invoke(null, (Object) LegacySkullMigration.writeNbt(legacyNbt));
            return rebuilt == null || rebuilt.getType().isAir() ? null : rebuilt;
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SearchableInfiniteShop] Could not rebuild a 1.15.2 " + legacyItem.get("type"), e);
            return null;
        }
    }

    /** The broken item is the same container with fewer items, or the first item of the box in place of the box. */
    static boolean looksBroken(ItemStack current, Map<?, ?> legacyItem, List<?> legacyContents) {
        Material legacyType = Material.matchMaterial(String.valueOf(legacyItem.get("type")));
        if (legacyType != null && current.getType() == legacyType) {
            if (!(current.getItemMeta() instanceof BlockStateMeta meta) || !meta.hasBlockState()
                    || !(meta.getBlockState() instanceof Container container)) return true;
            int items = 0;
            for (ItemStack content : container.getInventory().getContents()) {
                if (content != null && !content.getType().isAir()) items++;
            }
            return items < legacyContents.size();
        }
        if (legacyContents.get(0) instanceof Map<?, ?> first && first.get("id") instanceof String firstId) {
            Material firstType = Material.matchMaterial(firstId);
            return firstType != null && current.getType() == firstType;
        }
        return false;
    }

    static List<?> legacyContents(Map<String, Object> legacyNbt) {
        if (!(legacyNbt.get("tag") instanceof Map<?, ?> tag) || !(tag.get(BLOCK_ENTITY_TAG) instanceof Map<?, ?> blockEntity)) return null;
        return blockEntity.get("Items") instanceof List<?> items ? items : null;
    }

    /**
     * The 1.15.2 item NBT ({@code id}, {@code Count}, {@code tag}, {@code DataVersion}) of a raw legacy YAML item,
     * as the DataFixer expects it: the "internal" NBT plus the name and lore that Bukkit kept outside of it.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> legacyItemNbt(Map<?, ?> legacyItem) {
        if (!"org.bukkit.inventory.ItemStack".equals(legacyItem.get("=="))) return null;
        if (!(legacyItem.get("type") instanceof String type)) return null;
        if (!(legacyItem.get("meta") instanceof Map<?, ?> meta) || !(meta.get("internal") instanceof String internal)) return null;
        Map<String, Object> tag;
        try {
            tag = LegacySkullMigration.readNbt(Base64.getDecoder().decode(internal.replaceAll("\\s", "")));
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (!(tag.get(BLOCK_ENTITY_TAG) instanceof Map<?, ?>)) return null;

        Map<String, Object> display = tag.get("display") instanceof Map<?, ?> d
                ? (Map<String, Object>) d : new LinkedHashMap<>();
        if (meta.get("display-name") instanceof String name && !display.containsKey("Name")) display.put("Name", jsonText(name));
        if (meta.get("lore") instanceof List<?> lore && !display.containsKey("Lore")) {
            LegacySkullMigration.NbtList lines = new LegacySkullMigration.NbtList((byte) 8);
            for (Object line : lore) lines.add(jsonText(String.valueOf(line)));
            display.put("Lore", lines);
        }
        if (!display.isEmpty()) tag.put("display", display);
        renameLegacyAttributes(tag, 0);

        int amount = legacyItem.get("amount") instanceof Number n ? n.intValue() : 1;
        int version = legacyItem.get("v") instanceof Number n && n.intValue() > 0 ? n.intValue() : LEGACY_DATA_VERSION;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "minecraft:" + type.toLowerCase(Locale.ROOT));
        root.put("Count", (byte) Math.max(1, Math.min(127, amount)));
        root.put("tag", tag);
        root.put("DataVersion", version);
        return root;
    }

    /**
     * The items in the box may carry 1.15.2 camelCase attribute names ({@code generic.attackDamage}, e.g. MythicMobs 4
     * items), which Paper 1.21.11's DataFixer rejects together with the whole box. They are rewritten to the 1.16 form
     * the later fixes understand (same rule as {@link LegacyAttributeMigration}).
     */
    @SuppressWarnings("unchecked")
    static void renameLegacyAttributes(Object node, int depth) {
        if (depth > 64) return;
        if (node instanceof Map<?, ?> map) {
            if (map.get("AttributeModifiers") instanceof List<?> modifiers) {
                for (Object modifier : modifiers) {
                    if (modifier instanceof Map<?, ?> m && m.get("AttributeName") instanceof String name) {
                        ((Map<Object, Object>) m).put("AttributeName", legacyAttributeKey(name));
                    }
                }
            }
            for (Object value : map.values()) renameLegacyAttributes(value, depth + 1);
        } else if (node instanceof List<?> list) {
            for (Object value : list) renameLegacyAttributes(value, depth + 1);
        }
    }

    static String legacyAttributeKey(String raw) {
        String trimmed = raw.trim();
        String path = trimmed.substring(trimmed.indexOf(':') + 1);
        return "minecraft:" + path.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** 1.15.2 kept display names and lore as JSON text; Bukkit's YAML has the legacy "§" form. */
    private static String jsonText(String legacyText) {
        JsonObject json = new JsonObject();
        json.addProperty("text", legacyText);
        return json.toString();
    }

    private static volatile boolean checked;
    private static volatile Method deserializeBytes;

    private static Method deserializeBytesMethod() {
        if (!checked) {
            try {
                deserializeBytes = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            } catch (NoSuchMethodException e) {
                deserializeBytes = null; // Spigot
            }
            checked = true;
        }
        return deserializeBytes;
    }
}
