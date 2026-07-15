package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

public final class PageNavigationUtil {
    public static final int PREVIOUS_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final int TRADE_ROWS = 5;
    public static final int LIST_PAGE_SIZE = TRADE_ROWS * 9;
    public static final int SEARCH_PAGE_SIZE = TRADE_ROWS;
    private static final String NAVIGATION_TAG = "PageNavigation";
    private static final String PREVIOUS = "previous";
    private static final String NEXT = "next";

    private PageNavigationUtil() {
    }

    public static String title(String base, int page, int maxPage) {
        return ChatColor.DARK_BLUE + base + " " + LanguageKey.INVENTORY_PAGE_WITH_TOTAL.getMessage(page, Math.max(1, maxPage));
    }

    public static void setNavigationItems(Inventory inventory, int page, int maxPage) {
        if (page > 1) inventory.setItem(PREVIOUS_SLOT, createNavigationItem(PREVIOUS));
        if (page < maxPage) inventory.setItem(NEXT_SLOT, createNavigationItem(NEXT));
    }

    public static boolean isPrevious(ItemStack item) {
        return PREVIOUS.equals(NBTUtil.getNMSStringTag(item, NAVIGATION_TAG));
    }

    public static boolean isNext(ItemStack item) {
        return NEXT.equals(NBTUtil.getNMSStringTag(item, NAVIGATION_TAG));
    }

    private static ItemStack createNavigationItem(String direction) {
        String name = direction.equals(PREVIOUS)
                ? LanguageKey.ITEM_PAGE_PREVIOUS.getMessage()
                : LanguageKey.ITEM_PAGE_NEXT.getMessage();
        return NBTUtil.setNMSTag(
                ItemUtil.getNamedItem(Material.ARROW, ChatColor.GREEN + name),
                NAVIGATION_TAG,
                direction
        );
    }
}
