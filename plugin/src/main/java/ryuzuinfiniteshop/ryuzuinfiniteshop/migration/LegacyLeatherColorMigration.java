package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the dye of leather armor saved by 1.15.2 builds.
 * <p>
 * 1.15.2 wrote leather armor with {@code meta-type: LEATHER_ARMOR}. Current servers give leather helmets, chestplates,
 * leggings and boots a {@code COLORABLE_ARMOR} meta instead, and converting the deserialized LEATHER_ARMOR meta into
 * it only copies the color from another COLORABLE_ARMOR meta, so the dye was silently dropped.
 * Like {@link LegacySkullMigration}, the meta type has to be rewritten in the raw YAML text before Bukkit builds the
 * ItemStacks, see {@link #fixRawYaml(String)}. Files migrated before this step already lost the color; it is put back
 * from the shop's 1.15.2 backup by {@link #restore(YamlConfiguration, File, String)}.
 */
public final class LegacyLeatherColorMigration implements MigrationStep {
    public static final int FROM_VERSION = 6;
    public static final int TO_VERSION = 7;

    private static final String ITEM_STACK_CLASS = "org.bukkit.inventory.ItemStack";
    private static final Set<String> COLORABLE_ARMOR_TYPES = Set.of(
            "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS");

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
        return "Keep the dye of leather armor saved by 1.15.2";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The actual conversion needs the raw text and the backups (see ShopMigrationService); only the version is bumped here.
        yaml.set("data-version", TO_VERSION);
    }

    /**
     * @return the converted YAML text, or the input itself when nothing had to change.
     */
    public static String fixRawYaml(String text) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (RuntimeException e) {
            return text;
        }
        if (!fixNode(root)) return text;

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        return new Yaml(options).dump(root);
    }

    @SuppressWarnings("unchecked")
    private static boolean fixNode(Object node) {
        boolean changed = false;
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> m = (Map<Object, Object>) map;
            if (ITEM_STACK_CLASS.equals(m.get("==")) && COLORABLE_ARMOR_TYPES.contains(String.valueOf(m.get("type")))
                    && m.get("meta") instanceof Map<?, ?> meta && "LEATHER_ARMOR".equals(meta.get("meta-type"))) {
                ((Map<Object, Object>) meta).put("meta-type", "COLORABLE_ARMOR");
                changed = true;
            }
            for (Object value : m.values()) changed |= fixNode(value);
        } else if (node instanceof List<?> list) {
            for (Object value : list) changed |= fixNode(value);
        }
        return changed;
    }

    /**
     * Puts back the dye of leather armor that lost it in an earlier migration, taken from the item at the same
     * position in the shop's 1.15.2 backup. Only undyed items of the same material are touched, so armor dyed or
     * replaced since is left alone.
     *
     * @return how many items got their color back
     */
    public static int restore(YamlConfiguration yaml, File shopsDir, String fileName) {
        Object legacy = LegacyBackupMythicIdMigration.findLegacyBackup(shopsDir, fileName, "meta-type: LEATHER_ARMOR");
        if (!(legacy instanceof Map<?, ?> legacyRoot)) return 0;
        int count = 0;
        for (String key : yaml.getKeys(false)) count += restore(yaml.get(key), legacyRoot.get(key));
        return count;
    }

    static int restore(Object current, Object legacy) {
        if (legacy == null || current == null) return 0;
        if (current instanceof ItemStack item) return legacy instanceof Map<?, ?> legacyItem && restoreColor(item, legacyItem) ? 1 : 0;
        int count = 0;
        if (current instanceof ConfigurationSection section && legacy instanceof Map<?, ?> legacyMap) {
            for (String key : section.getKeys(false)) count += restore(section.get(key), legacyMap.get(key));
        } else if (current instanceof Map<?, ?> map && legacy instanceof Map<?, ?> legacyMap) {
            for (Map.Entry<?, ?> entry : map.entrySet()) count += restore(entry.getValue(), legacyMap.get(entry.getKey()));
        } else if (current instanceof List<?> list && legacy instanceof List<?> legacyList) {
            for (int i = 0; i < Math.min(list.size(), legacyList.size()); i++) count += restore(list.get(i), legacyList.get(i));
        }
        return count;
    }

    private static boolean restoreColor(ItemStack item, Map<?, ?> legacyItem) {
        if (!item.getType().name().equals(String.valueOf(legacyItem.get("type")))) return false;
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) return false;
        if (!meta.getColor().equals(Bukkit.getItemFactory().getDefaultLeatherColor())) return false;
        Color color = legacyColor(legacyItem);
        if (color == null || color.equals(meta.getColor())) return false;
        meta.setColor(color);
        item.setItemMeta(meta);
        return true;
    }

    private static Color legacyColor(Map<?, ?> legacyItem) {
        if (!(legacyItem.get("meta") instanceof Map<?, ?> meta) || !(meta.get("color") instanceof Map<?, ?> color)) return null;
        if (!(color.get("RED") instanceof Number r) || !(color.get("GREEN") instanceof Number g) || !(color.get("BLUE") instanceof Number b))
            return null;
        try {
            return Color.fromRGB(r.intValue(), g.intValue(), b.intValue());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
