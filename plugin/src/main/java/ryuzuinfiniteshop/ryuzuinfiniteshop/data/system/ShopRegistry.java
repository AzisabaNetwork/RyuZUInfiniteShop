package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import lombok.Getter;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Registry for all Shop instances, keyed by location-based ID.
 */
public final class ShopRegistry {
    @Getter
    private static final HashMap<String, Shop> shops = new HashMap<>();

    private ShopRegistry() {}

    public static Shop getShop(String id) {
        return shops.get(id);
    }

    public static void addShop(String id, Shop shop) {
        shops.put(id, shop);
    }

    public static void removeShop(String id) {
        shops.remove(id);
    }

    public static void clear() {
        shops.clear();
    }

    /**
     * Returns shops sorted by ID, optionally filtered by searchable/name.
     */
    public static LinkedHashMap<String, Shop> getSortedShops(boolean editMode, String name) {
        LinkedHashMap<String, Shop> sorted = new LinkedHashMap<>();
        shops.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .filter(key -> {
                    Shop shop = shops.get(key);
                    if (name != null && !shop.containsDisplayName(name)) return false;
                    return editMode || shop.isSearchable();
                })
                .forEach(key -> sorted.put(key, shops.get(key)));
        return sorted;
    }
}
