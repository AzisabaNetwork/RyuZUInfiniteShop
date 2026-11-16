package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MythicMobs IDs whose current definition generates a plain vanilla item (nothing but MythicMobs' own
 * {@code mythicmobs:*} tags). On 1.15.2 such items were ordinary items; keeping them as MythicMobs items would hand
 * out items that no longer stack with their vanilla counterparts, so the shop keeps them as the plain vanilla item.
 * <p>
 * Same rules as MythicItemFixer's {@code exclude-vanilla-definitions}.
 */
public final class VanillaMythicItems {
    /** Per lower-case ID; only answers from a generated item are cached. */
    private static final Map<String, Boolean> VANILLA = new ConcurrentHashMap<>();

    private VanillaMythicItems() {
    }

    public static void invalidate() {
        VANILLA.clear();
    }

    /** Whether the current MythicMobs generates a plain vanilla item for this ID. */
    public static boolean isVanillaDefinition(String id) {
        if (id == null || id.isBlank() || !MythicInstanceProvider.isLoaded()) return false;
        String key = id.trim().toLowerCase(Locale.ROOT);
        Boolean cached = VANILLA.get(key);
        if (cached != null) return cached;
        ItemStack generated;
        try {
            generated = MythicInstanceProvider.getInstance().getMythicItem(id.trim(), 1);
        } catch (RuntimeException e) {
            return false;
        }
        if (ItemUtil.isAir(generated)) return false; // unknown ID or MythicMobs still loading: ask again later
        boolean vanilla = isVanillaContent(generated);
        VANILLA.put(key, vanilla);
        return vanilla;
    }

    /**
     * The plain vanilla item {@code item} should be, or null: only when its MMID ({@code mythicmobs:type} or a
     * 1.15.2 {@code MYTHIC_TYPE}) has a plain vanilla definition, the item carries nothing but MythicMobs' tags on
     * top of the vanilla item, and there are tags to remove.
     */
    public static ItemStack revert(ItemStack item) {
        if (ItemUtil.isAir(item) || !item.hasItemMeta() || !MythicInstanceProvider.isLoaded()) return null;
        String id;
        try {
            id = MythicInstanceProvider.getInstance().getID(item.clone());
        } catch (RuntimeException e) {
            return null;
        }
        if (id == null || !isVanillaDefinition(id) || !isVanillaContent(item)) return null;
        ItemStack plain = new ItemStack(item.getType(), item.getAmount());
        return plain.isSimilar(item) ? null : plain;
    }

    /** The vanilla item a vanilla-definition MythicMobs item stands for (type and amount of the generated item). */
    public static ItemStack vanillaOf(ItemStack generated) {
        return new ItemStack(generated.getType(), generated.getAmount());
    }

    /**
     * True when the item equals {@code new ItemStack(type)} once MythicMobs' tags are gone: {@code mythicmobs:*}
     * PDC entries and MythicMobs 4 leftovers ({@code MYTHIC_*}, {@code Prevent*}) in custom data.
     */
    public static boolean isVanillaContent(ItemStack item) {
        if (ItemUtil.isAir(item)) return false;
        if (!item.hasItemMeta()) return true;
        ItemStack copy = item.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return true;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : new ArrayList<>(pdc.getKeys())) {
            if (!"mythicmobs".equals(key.getNamespace())) return false; // another plugin's data
            pdc.remove(key);
        }
        copy.setItemMeta(meta);
        Map<String, String> mine = components(copy.getItemMeta().getAsComponentString());
        ItemMeta vanillaMeta = new ItemStack(item.getType()).getItemMeta();
        Map<String, String> vanilla = vanillaMeta == null ? Map.of() : components(vanillaMeta.getAsComponentString());
        if (mine == null || vanilla == null) return false;
        Set<String> keys = new LinkedHashSet<>(mine.keySet());
        keys.addAll(vanilla.keySet());
        for (String key : keys) {
            String value = mine.get(key);
            if (java.util.Objects.equals(value, vanilla.get(key))) continue;
            if (!"minecraft:custom_data".equals(key) || value == null) return false;
            Set<String> dataKeys = compoundKeys(value);
            if (dataKeys == null) return false;
            for (String dataKey : dataKeys) {
                if (!dataKey.startsWith("MYTHIC") && !dataKey.startsWith("Prevent")) return false;
            }
        }
        return true;
    }

    /** {@code [a=1,b={c:2}]} → {a: "1", b: "{c:2}"}; null when malformed. */
    static Map<String, String> components(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.length() < 2 || s.charAt(0) != '[' || s.charAt(s.length() - 1) != ']') return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : split(s.substring(1, s.length() - 1))) {
            if (entry.isBlank()) continue;
            int eq = topLevelIndex(entry, '=');
            if (eq < 0) result.put(entry.trim(), ""); // "!minecraft:x" removals
            else result.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        return result;
    }

    /** Top-level keys of an SNBT compound {@code {a:1,"b c":{}}}; null when malformed. */
    static Set<String> compoundKeys(String snbt) {
        String s = snbt.trim();
        if (s.length() < 2 || s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') return null;
        Set<String> keys = new LinkedHashSet<>();
        for (String entry : split(s.substring(1, s.length() - 1))) {
            if (entry.isBlank()) continue;
            int colon = topLevelIndex(entry, ':');
            if (colon < 0) return null;
            String key = entry.substring(0, colon).trim();
            if (key.length() >= 2 && (key.charAt(0) == '"' || key.charAt(0) == '\'')) key = key.substring(1, key.length() - 1);
            keys.add(key);
        }
        return keys;
    }

    /** Splits on commas outside quotes, braces and brackets. */
    private static java.util.List<String> split(String s) {
        java.util.List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** First index of {@code target} outside quotes, braces and brackets, or -1. */
    private static int topLevelIndex(String s, char target) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == target && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
