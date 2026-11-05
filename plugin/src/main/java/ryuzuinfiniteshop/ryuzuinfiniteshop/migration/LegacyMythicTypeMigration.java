package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

/**
 * Restores MythicMobs items (and with them their attributes) saved by 1.15.2 builds.
 * <p>
 * Those items were saved as plain ItemStacks whose only link to MythicMobs is the {@code MYTHIC_TYPE} tag inside the
 * base64 "internal" NBT. Everything else (attributes, custom model data...) lived in MythicMobs' own item config.
 * Newer servers ignore "internal", so the ID was lost and no attributes could be regenerated.
 * <p>
 * Instead of rebuilding the ItemStack, each such item is rewritten as a {@code MythicItem} (id + amount), which the
 * shop resolves through MythicMobs at runtime. This works on the raw YAML text because the tag is dropped as soon
 * as Bukkit deserializes the ItemStack.
 */
public final class LegacyMythicTypeMigration implements MigrationStep {
    public static final int FROM_VERSION = 4;
    public static final int TO_VERSION = 5;

    static final String MYTHIC_ITEM_CLASS = "ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem";
    private static final String MYTHIC_TYPE = "MYTHIC_TYPE";

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
        return "Replace 1.15.2 MythicMobs item stacks with MythicItem references (restores their attributes)";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The actual conversion needs the raw text (see ShopMigrationService); only the version is bumped here.
        yaml.set("data-version", TO_VERSION);
    }

    /**
     * @return the converted YAML text, or the input itself when no legacy Mythic item was found.
     */
    public static String fixRawYaml(String text) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (RuntimeException e) {
            return text;
        }
        boolean[] changed = {false};
        Object fixed = fixNode(root, changed);
        if (!changed[0]) return text;

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        return new Yaml(options).dump(fixed);
    }

    @SuppressWarnings("unchecked")
    private static Object fixNode(Object node, boolean[] changed) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> m = (Map<Object, Object>) map;
            Map<String, Object> replacement = toMythicItem(m);
            if (replacement != null) {
                changed[0] = true;
                return replacement;
            }
            for (Map.Entry<Object, Object> entry : m.entrySet()) {
                entry.setValue(fixNode(entry.getValue(), changed));
            }
        } else if (node instanceof List<?> list) {
            List<Object> l = (List<Object>) list;
            l.replaceAll(value -> fixNode(value, changed));
        }
        return node;
    }

    private static Map<String, Object> toMythicItem(Map<?, ?> item) {
        if (!"org.bukkit.inventory.ItemStack".equals(item.get("=="))) return null;
        if (!(item.get("meta") instanceof Map<?, ?> meta) || !(meta.get("internal") instanceof String internal)) return null;

        Map<String, Object> nbt;
        try {
            nbt = LegacySkullMigration.readNbt(Base64.getDecoder().decode(internal.replaceAll("\s", "")));
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (!(nbt.get(MYTHIC_TYPE) instanceof String id) || id.isBlank()) return null;

        int amount = item.get("amount") instanceof Number n ? n.intValue() : 1;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("==", MYTHIC_ITEM_CLASS);
        result.put("id", id);
        result.put("amount", amount);
        return result;
    }
}
