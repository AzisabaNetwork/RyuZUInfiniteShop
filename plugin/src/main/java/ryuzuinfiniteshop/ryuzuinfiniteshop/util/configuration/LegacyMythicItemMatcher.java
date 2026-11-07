package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the MythicMobs ID of a shop item that lost every MythicMobs tag on the way from 1.15.2 to 1.21
 * (no {@code MYTHIC_TYPE}, no {@code mythicmobs:type}), by comparing it with the 1.15.2 MythicMobs {@code Items}
 * definitions placed in {@code plugins/SearchableInfiniteShop/legacy-mythic-items/}.
 * <p>
 * Compared: material (taken from the item the current MythicMobs generates for the ID, so numeric 1.15 IDs still
 * match), display name, lore and head skin. A head whose skin was lost still matches by name and lore.
 * Only unique matches are used, and a match is rejected when the item contradicts what the definition fixes
 * (custom model data, Unbreakable, leather colour).
 */
public final class LegacyMythicItemMatcher {
    public static final String DIRECTORY = "legacy-mythic-items";
    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static LegacyMythicItemMatcher instance;
    private static boolean attempted;

    private final Map<Signature, String> ids = new HashMap<>();
    private final Map<Signature, String> plainIds = new HashMap<>();
    private final Map<Signature, String> skinlessHeadIds = new HashMap<>();
    private final Map<String, Traits> traitsById = new HashMap<>();
    private final Set<Signature> ambiguous = new HashSet<>();
    private final Set<Signature> plainAmbiguous = new HashSet<>();
    private final Set<Signature> skinlessHeadAmbiguous = new HashSet<>();
    private int files;
    private int missingCurrent;

    /**
     * @return the MythicMobs ID of a legacy item that lost its ID, or null when there is no unique match
     * (or no legacy definitions / MythicMobs are available)
     */
    public static String findId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        LegacyMythicItemMatcher matcher = get();
        return matcher == null ? null : matcher.match(item);
    }

    /** Forgets the loaded definitions; they are read again on next use (after a reload of SIS or MythicMobs). */
    public static synchronized void invalidate() {
        instance = null;
        attempted = false;
    }

    private static synchronized LegacyMythicItemMatcher get() {
        if (attempted) return instance;
        if (!MythicInstanceProvider.isLoaded()) return null;
        attempted = true;
        RyuZUInfiniteShop plugin = RyuZUInfiniteShop.getPlugin();
        if (plugin == null) return null;
        File directory = new File(plugin.getDataFolder(), DIRECTORY);
        if (!directory.isDirectory()) return null;
        try {
            instance = load(directory, id -> {
                try {
                    return MythicInstanceProvider.getInstance().getMythicItem(id, 1);
                } catch (RuntimeException e) {
                    return null;
                }
            });
            plugin.getLogger().info("Legacy MythicMobs item definitions loaded: " + instance.summary());
        } catch (IOException | RuntimeException e) {
            instance = null;
            plugin.getLogger().warning("Could not load legacy MythicMobs item definitions from " + directory + ": " + e.getMessage());
        }
        return instance;
    }

    /**
     * @param currentItem the item the current MythicMobs generates for an ID, or null if it has none
     */
    static LegacyMythicItemMatcher load(File directory, Function<String, ItemStack> currentItem) throws IOException {
        LegacyMythicItemMatcher result = new LegacyMythicItemMatcher();
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))::iterator) {
                result.files++;
                Object document;
                try {
                    document = new Yaml(new SafeConstructor(new LoaderOptions()))
                            .load(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                } catch (RuntimeException invalidYaml) {
                    continue;
                }
                if (!(document instanceof Map<?, ?> map)) continue;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String id && entry.getValue() instanceof Map<?, ?> config)
                        result.add(id, config, currentItem);
                }
            }
        }
        result.ambiguous.forEach(result.ids::remove);
        result.plainAmbiguous.forEach(result.plainIds::remove);
        result.skinlessHeadAmbiguous.forEach(result.skinlessHeadIds::remove);
        return result;
    }

    private void add(String id, Map<?, ?> config, Function<String, ItemStack> currentItem) {
        if (config.get("Id") == null) return;
        ItemStack current = currentItem.apply(id);
        if (current == null || current.getType().isAir()) {
            missingCurrent++;
            return;
        }
        traitsById.put(id, Traits.fromDefinition(config, current));
        Signature signature = Signature.fromDefinition(config, current.getType());
        if (signature == null) return;
        String previous = ids.putIfAbsent(signature, id);
        if (previous != null && !previous.equals(id)) ambiguous.add(signature);
        Signature plain = signature.plain();
        String previousPlain = plainIds.putIfAbsent(plain, id);
        if (previousPlain != null && !previousPlain.equals(id)) plainAmbiguous.add(plain);
        if (signature.skinUrl != null && (signature.name != null || !signature.lore.isEmpty())) {
            Signature withoutSkin = plain.withoutSkin();
            String previousHead = skinlessHeadIds.putIfAbsent(withoutSkin, id);
            if (previousHead != null && !previousHead.equals(id)) skinlessHeadAmbiguous.add(withoutSkin);
        }
    }

    String match(ItemStack item) {
        Signature signature = Signature.fromItem(item);
        if (signature == null) return null;
        String id = ids.get(signature);
        if (id == null) id = plainIds.get(signature.plain());
        if (id == null && signature.material == Material.PLAYER_HEAD && signature.skinUrl == null
                && (signature.name != null || !signature.lore.isEmpty())) {
            id = skinlessHeadIds.get(signature.plain().withoutSkin());
        }
        if (id == null) return null;
        Traits traits = traitsById.get(id);
        return traits != null && !traits.allows(item) ? null : id;
    }

    String summary() {
        return files + " files, " + ids.size() + " unique definitions, " + ambiguous.size()
                + " ambiguous, " + missingCurrent + " IDs missing in current MythicMobs";
    }

    static String readableText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return value;
        try {
            return BaseComponent.toLegacyText(ComponentSerializer.parse(value));
        } catch (RuntimeException invalidComponent) {
            return value;
        }
    }

    private static Integer integer(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw == null) return null;
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** What a definition fixes on every item it generates, used to rule out look-alikes. */
    private static final class Traits {
        final Set<Integer> models = new HashSet<>();
        final boolean unbreakable;
        final Color color;

        private Traits(boolean unbreakable, Color color) {
            this.unbreakable = unbreakable;
            this.color = color;
        }

        @SuppressWarnings("deprecation")
        static Traits fromDefinition(Map<?, ?> config, ItemStack current) {
            Integer model = integer(config.containsKey("Model") ? config.get("Model") : config.get("CustomModelData"));
            Map<?, ?> options = config.get("Options") instanceof Map<?, ?> o ? o : Collections.emptyMap();
            Traits traits = new Traits(Boolean.parseBoolean(String.valueOf(options.get("Unbreakable"))), color(options.get("Color")));
            if (model != null) traits.models.add(model);
            ItemMeta meta = current.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) traits.models.add(meta.getCustomModelData());
            return traits;
        }

        @SuppressWarnings("deprecation")
        boolean allows(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;
            if (meta.hasCustomModelData() && !models.contains(meta.getCustomModelData())) return false;
            if (unbreakable && !meta.isUnbreakable()) return false;
            return color == null || !(meta instanceof LeatherArmorMeta leather) || color.equals(leather.getColor());
        }

        private static Color color(Object raw) {
            if (raw == null) return null;
            String[] parts = String.valueOf(raw).split(",");
            if (parts.length != 3) return null;
            try {
                return Color.fromRGB(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record Signature(Material material, String name, List<String> lore, String skinUrl) {
        static Signature fromDefinition(Map<?, ?> config, Material material) {
            String name = colored(config.get("Display"));
            List<String> lore = coloredList(config.get("Lore"));
            if (name == null && lore.isEmpty() && material != Material.PLAYER_HEAD) return null;
            String skinUrl = null;
            if (material == Material.PLAYER_HEAD) {
                if (!(config.get("Options") instanceof Map<?, ?> options)) return null;
                skinUrl = decodeSkinUrl(options.get("SkinTexture"));
                if (skinUrl == null) return null;
            }
            return new Signature(material, name, lore, skinUrl);
        }

        @SuppressWarnings("deprecation")
        static Signature fromItem(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String name = meta.hasDisplayName() ? readableText(meta.getDisplayName()) : null;
            List<String> lore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) lore.add(readableText(line));
            }
            if (name == null && lore.isEmpty() && item.getType() != Material.PLAYER_HEAD) return null;
            String skinUrl = null;
            if (item.getType() == Material.PLAYER_HEAD) {
                if (!(meta instanceof SkullMeta skull)) return null;
                skinUrl = itemSkinUrl(skull);
                if (skinUrl == null && name == null && lore.isEmpty()) return null;
            }
            return new Signature(item.getType(), name, lore, skinUrl);
        }

        private static String colored(Object value) {
            return value == null ? null : ChatColor.translateAlternateColorCodes('&', String.valueOf(value));
        }

        private static List<String> coloredList(Object value) {
            if (!(value instanceof List<?> list)) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (Object line : list) result.add(colored(line));
            return result;
        }

        private static String decodeSkinUrl(Object encoded) {
            if (encoded == null) return null;
            try {
                String json = new String(Base64.getDecoder().decode(String.valueOf(encoded).trim()), StandardCharsets.UTF_8);
                Matcher matcher = SKIN_URL.matcher(json);
                return matcher.find() ? matcher.group(1) : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static String itemSkinUrl(SkullMeta meta) {
            PlayerProfile profile = meta.getOwnerProfile();
            if (profile == null) return null;
            URL skin = profile.getTextures().getSkin();
            return skin == null ? null : skin.toString();
        }

        Signature plain() {
            List<String> plainLore = new ArrayList<>(lore.size());
            for (String line : lore) plainLore.add(strip(line));
            return new Signature(material, strip(name), plainLore, skinUrl);
        }

        Signature withoutSkin() {
            return new Signature(material, name, lore, null);
        }

        private static String strip(String value) {
            return value == null ? null : ChatColor.stripColor(value).trim();
        }
    }
}
