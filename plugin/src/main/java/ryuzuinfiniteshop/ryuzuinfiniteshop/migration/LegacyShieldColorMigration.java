package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ShieldMeta;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the base color and banner patterns of shields saved by 1.15.2 builds.
 * <p>
 * 1.15.2 wrote shields with {@code meta-type: TILE_ENTITY}; their colors only lived in the {@code BlockEntityTag} of
 * the base64 "internal" NBT, as numeric {@code Base}/{@code Patterns[].Color} dye ids. Current servers read that tag
 * as-is without running the data fixers, so neither the legacy {@code Base} nor the legacy {@code Patterns} list is
 * understood and every dyed shield came back plain white.
 * Like {@link LegacyLeatherColorMigration}, the item meta is rewritten in the raw YAML text into the current
 * {@code SHIELD} meta before Bukkit builds the ItemStacks, see {@link #fixRawYaml(String)}. Files migrated before this
 * step already lost the colors; they are put back from the shop's 1.15.2 backup by
 * {@link #restore(YamlConfiguration, File, String)}.
 */
public final class LegacyShieldColorMigration implements MigrationStep {
    public static final int FROM_VERSION = 7;
    public static final int TO_VERSION = 8;

    private static final String ITEM_STACK_CLASS = "org.bukkit.inventory.ItemStack";
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    /** Dye ids as used by 1.13 - 1.20.4 banner NBT (= wool data). */
    private static final DyeColor[] DYE_BY_ID = {
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
            DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
            DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK};

    /** Legacy pattern codes still accepted by {@code Pattern}'s deserializer, mapped to their current keys. */
    private static final Map<String, String> PATTERN_KEYS = Map.ofEntries(
            Map.entry("b", "base"), Map.entry("bl", "square_bottom_left"), Map.entry("br", "square_bottom_right"),
            Map.entry("tl", "square_top_left"), Map.entry("tr", "square_top_right"), Map.entry("bs", "stripe_bottom"),
            Map.entry("ts", "stripe_top"), Map.entry("ls", "stripe_left"), Map.entry("rs", "stripe_right"),
            Map.entry("cs", "stripe_center"), Map.entry("ms", "stripe_middle"), Map.entry("drs", "stripe_downright"),
            Map.entry("dls", "stripe_downleft"), Map.entry("ss", "small_stripes"), Map.entry("cr", "cross"),
            Map.entry("sc", "straight_cross"), Map.entry("bt", "triangle_bottom"), Map.entry("tt", "triangle_top"),
            Map.entry("bts", "triangles_bottom"), Map.entry("tts", "triangles_top"), Map.entry("ld", "diagonal_left"),
            Map.entry("rd", "diagonal_up_right"), Map.entry("lud", "diagonal_up_left"), Map.entry("rud", "diagonal_right"),
            Map.entry("mc", "circle"), Map.entry("mr", "rhombus"), Map.entry("vh", "half_vertical"),
            Map.entry("hh", "half_horizontal"), Map.entry("vhr", "half_vertical_right"),
            Map.entry("hhb", "half_horizontal_bottom"), Map.entry("bo", "border"), Map.entry("cbo", "curly_border"),
            Map.entry("cre", "creeper"), Map.entry("gra", "gradient"), Map.entry("gru", "gradient_up"),
            Map.entry("bri", "bricks"), Map.entry("sku", "skull"), Map.entry("flo", "flower"), Map.entry("moj", "mojang"),
            Map.entry("glb", "globe"), Map.entry("pig", "piglin"), Map.entry("flw", "flow"), Map.entry("gus", "guster"));

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
        return "Keep the base color and patterns of shields saved by 1.15.2";
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
            if (ITEM_STACK_CLASS.equals(m.get("==")) && "SHIELD".equals(String.valueOf(m.get("type")))
                    && m.get("meta") instanceof Map<?, ?> meta && "TILE_ENTITY".equals(meta.get("meta-type"))
                    && fixShieldMeta((Map<Object, Object>) meta)) {
                changed = true;
            }
            for (Object value : m.values()) changed |= fixNode(value);
        } else if (node instanceof List<?> list) {
            for (Object value : list) changed |= fixNode(value);
        }
        return changed;
    }

    private static boolean fixShieldMeta(Map<Object, Object> meta) {
        if (!(meta.get("internal") instanceof String internal)) return false;
        Map<String, Object> nbt;
        try {
            nbt = LegacySkullMigration.readNbt(Base64.getDecoder().decode(internal.replaceAll("\\s", "")));
        } catch (IOException | RuntimeException e) {
            return false;
        }
        if (!(nbt.get(BLOCK_ENTITY_TAG) instanceof Map<?, ?> blockEntity)) return false;
        LegacyShield shield = LegacyShield.of(blockEntity);

        nbt.remove(BLOCK_ENTITY_TAG);
        if (nbt.isEmpty()) {
            meta.remove("internal");
        } else {
            try {
                meta.put("internal", Base64.getEncoder().encodeToString(LegacySkullMigration.writeNbt(nbt)));
            } catch (IOException e) {
                return false;
            }
        }
        meta.remove("blockMaterial");
        meta.put("meta-type", "SHIELD");
        meta.put("base-color", shield.base().name());
        if (!shield.patterns().isEmpty()) {
            List<Map<String, Object>> patterns = new ArrayList<>();
            for (LegacyPattern pattern : shield.patterns()) {
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("==", "Pattern");
                serialized.put("color", pattern.color().name());
                serialized.put("pattern", "minecraft:" + pattern.key());
                patterns.add(serialized);
            }
            meta.put("patterns", patterns);
        }
        return true;
    }

    /**
     * Puts back the colors of shields that lost them in an earlier migration, taken from the item at the same position
     * in the shop's 1.15.2 backup. Only shields still plain (white or no base color, no patterns) are touched, so
     * shields recolored or replaced since are left alone.
     *
     * @return how many items got their colors back
     */
    public static int restore(YamlConfiguration yaml, File shopsDir, String fileName) {
        Object legacy = LegacyBackupMythicIdMigration.findLegacyBackup(shopsDir, fileName, "blockMaterial: SHIELD");
        if (!(legacy instanceof Map<?, ?> legacyRoot)) return 0;
        int count = 0;
        for (String key : yaml.getKeys(false)) count += restore(yaml.get(key), legacyRoot.get(key));
        return count;
    }

    static int restore(Object current, Object legacy) {
        if (legacy == null || current == null) return 0;
        if (current instanceof ItemStack item) return legacy instanceof Map<?, ?> legacyItem && restoreColors(item, legacyItem) ? 1 : 0;
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

    private static boolean restoreColors(ItemStack item, Map<?, ?> legacyItem) {
        if (item.getType() != Material.SHIELD || !"SHIELD".equals(String.valueOf(legacyItem.get("type")))) return false;
        if (!(item.getItemMeta() instanceof ShieldMeta meta)) return false;
        if (meta.numberOfPatterns() > 0 || (meta.getBaseColor() != null && meta.getBaseColor() != DyeColor.WHITE)) return false;
        LegacyShield shield = legacyShield(legacyItem);
        if (shield == null) return false;
        if (shield.base() == meta.getBaseColor() && shield.patterns().isEmpty()) return false;

        meta.setBaseColor(shield.base());
        List<Pattern> patterns = new ArrayList<>();
        for (LegacyPattern pattern : shield.patterns()) {
            PatternType type = Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(pattern.key()));
            if (type != null) patterns.add(new Pattern(pattern.color(), type));
        }
        meta.setPatterns(patterns);
        item.setItemMeta(meta);
        return true;
    }

    private static LegacyShield legacyShield(Map<?, ?> legacyItem) {
        if (!(legacyItem.get("meta") instanceof Map<?, ?> meta) || !(meta.get("internal") instanceof String internal)) return null;
        try {
            Map<String, Object> nbt = LegacySkullMigration.readNbt(Base64.getDecoder().decode(internal.replaceAll("\\s", "")));
            return nbt.get(BLOCK_ENTITY_TAG) instanceof Map<?, ?> blockEntity ? LegacyShield.of(blockEntity) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static DyeColor dye(Object id) {
        return id instanceof Number n && n.intValue() >= 0 && n.intValue() < DYE_BY_ID.length ? DYE_BY_ID[n.intValue()] : null;
    }

    private record LegacyPattern(DyeColor color, String key) {
    }

    private record LegacyShield(DyeColor base, List<LegacyPattern> patterns) {
        /** 1.15.2 drew any shield with a BlockEntityTag as a banner, defaulting a missing {@code Base} to white. */
        static LegacyShield of(Map<?, ?> blockEntity) {
            DyeColor base = dye(blockEntity.get("Base"));
            List<LegacyPattern> patterns = new ArrayList<>();
            if (blockEntity.get("Patterns") instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> p)) continue;
                    DyeColor color = dye(p.get("Color"));
                    String key = PATTERN_KEYS.get(String.valueOf(p.get("Pattern")));
                    if (color != null && key != null) patterns.add(new LegacyPattern(color, key));
                }
            }
            return new LegacyShield(base != null ? base : DyeColor.WHITE, patterns);
        }
    }
}
