package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;

import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * Finds the MythicMobs ID of a shop item that lost every MythicMobs tag (no {@code MYTHIC_TYPE}, no
 * {@code mythicmobs:type}) by asking the running MythicMobs for every item it has and comparing them with the shop
 * item. Unlike {@link LegacyMythicItemMatcher} this needs no copy of the 1.15.2 definitions; it works for every item
 * whose definition still generates the same name and lore.
 * <p>
 * Compared: material, display name, lore (colour codes ignored as a fallback) and head skin. A head whose skin was
 * lost still matches by name and lore. Only unique matches are used. A match is rejected when the item contradicts
 * what the current definition fixes (custom model data, Unbreakable, leather colour). Definitions without a name
 * and lore, and definitions that generate a different item every time (random lore etc.), are never matched.
 */
public final class CurrentMythicItemMatcher {
    private static CurrentMythicItemMatcher instance;
    private static boolean attempted;
    /** Plain lore of the item the current MythicMobs generates per ID; empty when it has no such item. */
    private static final Map<String, Optional<List<String>>> currentLore = new HashMap<>();

    /** Every ID per signature; a signature identifies an item only when exactly one ID has it. */
    private final Map<Signature, Set<String>> ids = new HashMap<>();
    private final Map<Signature, Set<String>> plainIds = new HashMap<>();
    private final Map<Signature, Set<String>> skinlessHeadIds = new HashMap<>();
    /** IDs per plain display name, only used to explain why an item did not match. */
    private final Map<String, Set<String>> idsByPlainName = new HashMap<>();
    private final Map<String, Signature> signatureById = new HashMap<>();
    private final Map<String, Traits> traitsById = new HashMap<>();
    private final Set<String> variableIds = new HashSet<>();
    private int definitions;
    private int unusable;

    /**
     * @return the MythicMobs ID of an item that lost its ID, or null when there is no unique match
     * (or MythicMobs is not available)
     */
    public static String findId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        CurrentMythicItemMatcher matcher = get();
        return matcher == null ? null : matcher.match(item);
    }

    /** Forgets the index; it is built again on next use (after a reload of SIS or MythicMobs). */
    public static synchronized void invalidate() {
        instance = null;
        attempted = false;
        currentLore.clear();
    }

    /**
     * Whether the item's lore has the same text (colour codes ignored) as the item the current MythicMobs generates
     * for the ID. False when MythicMobs has no item with that ID.
     */
    public static boolean hasSameLoreAsCurrent(ItemStack item, String id) {
        Optional<List<String>> lore = currentPlainLore(id);
        return lore != null && lore.isPresent() && lore.get().equals(plainLore(item));
    }

    /** @return the plain lore the current MythicMobs generates for the ID, empty if it has no such item, null without MythicMobs */
    public static Optional<List<String>> currentPlainLore(String id) {
        if (id == null || !MythicInstanceProvider.isLoaded()) return null;
        synchronized (CurrentMythicItemMatcher.class) {
            return currentLore.computeIfAbsent(id, key -> {
                try {
                    ItemStack current = MythicInstanceProvider.getInstance().getMythicItem(key, 1);
                    return current == null || current.getType().isAir() ? Optional.empty() : Optional.of(plainLore(current));
                } catch (RuntimeException e) {
                    return Optional.empty();
                }
            });
        }
    }

    /** The lore lines without colour codes and surrounding spaces. */
    @SuppressWarnings("deprecation")
    public static List<String> plainLore(ItemStack item) {
        List<String> result = new ArrayList<>();
        ItemMeta meta = item == null || !item.hasItemMeta() ? null : item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return result;
        for (String line : meta.getLore()) result.add(Signature.strip(LegacyMythicItemMatcher.readableText(line)));
        return result;
    }

    private static synchronized CurrentMythicItemMatcher get() {
        if (attempted) return instance;
        if (!MythicInstanceProvider.isLoaded()) return null;
        RyuZUInfiniteShop plugin = RyuZUInfiniteShop.getPlugin();
        try {
            Collection<String> names = MythicInstanceProvider.getInstance().getMythicItemNames();
            // MythicMobs may not have loaded its items yet: try again on next use instead of giving up.
            if (names == null || names.isEmpty()) return null;
            attempted = true;
            long started = System.nanoTime();
            instance = load(names, id -> {
                try {
                    return MythicInstanceProvider.getInstance().getMythicItem(id, 1);
                } catch (RuntimeException e) {
                    return null;
                }
            });
            if (plugin != null) plugin.getLogger().info("Current MythicMobs items indexed in "
                    + (System.nanoTime() - started) / 1_000_000L + " ms: " + instance.summary());
        } catch (RuntimeException e) {
            attempted = true;
            instance = null;
            if (plugin != null) plugin.getLogger().warning("Could not index the current MythicMobs items: " + e);
        }
        return instance;
    }

    /**
     * @param generator the item the current MythicMobs generates for an ID, or null if it cannot
     */
    static CurrentMythicItemMatcher load(Collection<String> names, Function<String, ItemStack> generator) {
        CurrentMythicItemMatcher result = new CurrentMythicItemMatcher();
        for (String id : names) result.add(id, generator);
        return result;
    }

    private void add(String id, Function<String, ItemStack> generator) {
        definitions++;
        ItemStack first = generator.apply(id);
        if (first == null || first.getType().isAir()) {
            unusable++;
            return;
        }
        Signature signature = Signature.fromItem(first);
        if (signature == null || (signature.name == null && signature.lore.isEmpty())) {
            unusable++;
            return;
        }
        Signature plain = signature.plain();
        signatureById.put(id, signature);
        if (plain.name != null) idsByPlainName.computeIfAbsent(plain.name, k -> new TreeSet<>()).add(id);
        // A definition that generates something else every time cannot be recognised by its looks.
        ItemStack second = generator.apply(id);
        if (second == null || !signature.equals(Signature.fromItem(second))) {
            variableIds.add(id);
            return;
        }
        traitsById.put(id, Traits.fromItem(first));
        ids.computeIfAbsent(signature, k -> new TreeSet<>()).add(id);
        plainIds.computeIfAbsent(plain, k -> new TreeSet<>()).add(id);
        if (signature.skinUrl != null) skinlessHeadIds.computeIfAbsent(plain.withoutSkin(), k -> new TreeSet<>()).add(id);
    }

    /** @return every ID generating an item that looks like this one (more than one means ambiguous) */
    private Set<String> candidates(Signature signature) {
        Set<String> found = ids.get(signature);
        if (found == null) found = plainIds.get(signature.plain());
        if (found == null && signature.material == Material.PLAYER_HEAD && signature.skinUrl == null)
            found = skinlessHeadIds.get(signature.plain().withoutSkin());
        return found == null ? Collections.emptySet() : found;
    }

    String match(ItemStack item) {
        Signature signature = Signature.fromItem(item);
        if (signature == null || (signature.name == null && signature.lore.isEmpty())) return null;
        Set<String> found = candidates(signature);
        if (found.size() != 1) return null;
        String id = found.iterator().next();
        Traits traits = traitsById.get(id);
        return traits != null && !traits.allows(item) ? null : id;
    }

    /** Explains, for {@code /sis mmid}, which MythicMobs item this one matches or why none does. */
    public static List<String> explain(ItemStack item) {
        List<String> result = new ArrayList<>();
        CurrentMythicItemMatcher matcher = get();
        if (matcher == null) {
            result.add("index unavailable (MythicMobs not loaded or has no items)");
            return result;
        }
        result.add("index: " + matcher.summary());
        Signature signature = item == null || !item.hasItemMeta() ? null : Signature.fromItem(item);
        if (signature == null || (signature.name == null && signature.lore.isEmpty())) {
            result.add("item has no name and lore");
            return result;
        }
        Set<String> found = matcher.candidates(signature);
        if (found.size() > 1) {
            result.add("ambiguous, same look as: " + String.join(", ", found));
            return result;
        }
        if (found.size() == 1) {
            String id = found.iterator().next();
            Traits traits = matcher.traitsById.get(id);
            String reason = traits == null ? null : traits.rejection(item);
            result.add(reason == null ? "matches " + id : "looks like " + id + " but " + reason);
            return result;
        }
        String plainName = signature.plain().name;
        Set<String> sameName = plainName == null ? Set.of() : matcher.idsByPlainName.getOrDefault(plainName, Set.of());
        if (sameName.isEmpty()) {
            result.add("no MythicMobs item is named '" + plainName + "'");
            return result;
        }
        for (String id : sameName) {
            Signature other = matcher.signatureById.get(id);
            List<String> diff = new ArrayList<>();
            if (matcher.variableIds.contains(id)) diff.add("generates a different item every time");
            if (other.material != signature.material) diff.add("material " + other.material + " vs " + signature.material);
            if (!other.plain().lore.equals(signature.plain().lore))
                diff.add("lore " + other.plain().lore + " vs " + signature.plain().lore);
            if (other.skinUrl != null && signature.skinUrl != null && !other.skinUrl.equals(signature.skinUrl))
                diff.add("head skin differs");
            result.add("same name as " + id + ": " + (diff.isEmpty() ? "no difference found" : String.join("; ", diff)));
        }
        return result;
    }

    String summary() {
        long unique = ids.values().stream().filter(set -> set.size() == 1).count();
        return definitions + " items, " + unique + " unique looks, " + (ids.size() - unique) + " shared looks, "
                + variableIds.size() + " variable, " + unusable + " without name/lore";
    }

    /** What the current definition fixes on every item it generates, used to rule out look-alikes. */
    private record Traits(Integer model, boolean unbreakable, Color color) {
        @SuppressWarnings("deprecation")
        static Traits fromItem(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return new Traits(null, false, null);
            Integer model = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
            Color color = meta instanceof LeatherArmorMeta leather ? leather.getColor() : null;
            return new Traits(model, meta.isUnbreakable(), color);
        }

        boolean allows(ItemStack item) {
            return rejection(item) == null;
        }

        /** @return why the item cannot be this definition's item, or null if it can */
        @SuppressWarnings("deprecation")
        String rejection(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            if (meta.hasCustomModelData() && !Objects.equals(model, meta.getCustomModelData()))
                return "custom model data " + meta.getCustomModelData() + " (MythicMobs: " + model + ")";
            if (unbreakable && !meta.isUnbreakable()) return "it is not unbreakable (MythicMobs: Unbreakable)";
            if (color != null && meta instanceof LeatherArmorMeta leather && !color.equals(leather.getColor()))
                return "leather colour " + leather.getColor() + " (MythicMobs: " + color + ")";
            return null;
        }
    }

    private record Signature(Material material, String name, List<String> lore, String skinUrl) {
        @SuppressWarnings("deprecation")
        static Signature fromItem(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String name = meta.hasDisplayName() ? LegacyMythicItemMatcher.readableText(meta.getDisplayName()) : null;
            List<String> lore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) lore.add(LegacyMythicItemMatcher.readableText(line));
            }
            String skinUrl = item.getType() == Material.PLAYER_HEAD && meta instanceof SkullMeta skull ? skinUrl(skull) : null;
            return new Signature(item.getType(), name, lore, skinUrl);
        }

        private static String skinUrl(SkullMeta meta) {
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

        static String strip(String value) {
            if (value == null) return null;
            String stripped = ChatColor.stripColor(value).trim();
            return stripped.isEmpty() ? null : stripped;
        }
    }
}
