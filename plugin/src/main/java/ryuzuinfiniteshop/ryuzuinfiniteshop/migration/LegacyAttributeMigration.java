package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Restores attribute modifiers saved by 1.15.2 builds.
 * <p>
 * 1.15.2 wrote {@code attribute-modifiers} keyed by the NMS name ({@code generic.attackDamage}) with modifiers
 * identified by {@code uuid}, and {@code slot: HAND}. Newer servers expect registry keys
 * ({@code minecraft:attack_damage}) and the {@code HAND} slot now means "both hands", so the whole map was
 * discarded while the ItemStack was deserialized.
 * Like {@link LegacySkullMigration}, this has to rewrite the raw YAML text before Bukkit builds the ItemStacks,
 * see {@link #fixRawYaml(String)}.
 */
public final class LegacyAttributeMigration implements MigrationStep {
    public static final int FROM_VERSION = 3;
    public static final int TO_VERSION = 4;

    private static final String ATTRIBUTE_MODIFIERS = "attribute-modifiers";

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
        return "Convert attribute modifiers saved by 1.15.2 to the current attribute keys";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The actual conversion needs the raw text (see ShopMigrationService); only the version is bumped here.
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
            if (m.get(ATTRIBUTE_MODIFIERS) instanceof Map<?, ?> modifiers
                    && fixModifiers(m, (Map<Object, Object>) modifiers)) {
                changed = true;
            }
            for (Object value : m.values()) changed |= fixNode(value);
        } else if (node instanceof List<?> list) {
            for (Object value : list) changed |= fixNode(value);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean fixModifiers(Map<Object, Object> meta, Map<Object, Object> modifiers) {
        Map<Object, Object> converted = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<Object, Object> entry : modifiers.entrySet()) {
            String oldKey = String.valueOf(entry.getKey());
            String key = convertAttributeKey(oldKey);
            if (!key.equals(oldKey)) changed = true;

            List<Object> list = (List<Object>) converted.computeIfAbsent(key, k -> new ArrayList<>());
            if (entry.getValue() instanceof List<?> values) {
                for (Object value : values) {
                    if (value instanceof Map<?, ?> mod) {
                        changed |= convertModifier((Map<Object, Object>) mod);
                    }
                    list.add(value);
                }
            }
        }
        if (changed) meta.put(ATTRIBUTE_MODIFIERS, converted);
        return changed;
    }

    private static boolean convertModifier(Map<Object, Object> modifier) {
        boolean changed = false;
        if (modifier.containsKey("uuid")) {
            // The current API identifies a modifier by a NamespacedKey; the uuid string is a valid key value.
            String id = String.valueOf(modifier.remove("uuid")).toLowerCase(Locale.ROOT);
            modifier.remove("name");
            try {
                id = UUID.fromString(id).toString();
            } catch (IllegalArgumentException ignored) {
                // keep as is
            }
            modifier.putIfAbsent("key", "minecraft:" + id);
            changed = true;
        }
        if (modifier.get("slot") instanceof String slot) {
            // 1.15.2 HAND/OFF_HAND were single slots; the new "hand" group means both hands.
            String converted = switch (slot.toUpperCase(Locale.ROOT)) {
                case "HAND" -> "mainhand";
                case "OFF_HAND" -> "offhand";
                default -> slot;
            };
            if (!converted.equals(slot)) {
                modifier.put("slot", converted);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * {@code generic.attackDamage}, {@code GENERIC_ATTACK_DAMAGE} and {@code minecraft:generic.attack_damage}
     * all become {@code minecraft:attack_damage}.
     */
    static String convertAttributeKey(String raw) {
        String name = raw.trim();
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);

        if (name.contains(".")) {
            name = name.substring(name.indexOf('.') + 1);
        } else if (name.equals(name.toUpperCase(Locale.ROOT))) {
            // Enum style: GENERIC_ATTACK_DAMAGE, HORSE_JUMP_STRENGTH, ZOMBIE_SPAWN_REINFORCEMENTS
            for (String prefix : new String[]{"GENERIC_", "HORSE_", "ZOMBIE_", "PLAYER_"}) {
                if (name.startsWith(prefix)) {
                    name = name.substring(prefix.length());
                    break;
                }
            }
        }
        name = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return "minecraft:" + name;
    }
}
