package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopListHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

//ショップエディターのメインページ
public class ShopListGui extends PageableGui {
    protected final LinkedHashMap<String, Shop> shops;

    public ShopListGui(int page, LinkedHashMap<String, Shop> shops) {
        super(page);
        this.shops = shops;
    }

    @Override
    public Inventory getInventory(ShopMode mode) {
        int maxPage = Math.max(1, (int) Math.ceil((double) shops.size() / PageNavigationUtil.LIST_PAGE_SIZE));
        Inventory inv = Bukkit.createInventory(new ShopListHolder(mode, this, shops), 9 * 6, PageNavigationUtil.title(LanguageKey.INVENTORY_SHOP_LIST.getMessage(), getPage(), maxPage));

        List<String> keys = new ArrayList<>(shops.keySet());
        for (int i = 0; i < Math.min(shops.size() - (getPage() - 1) * PageNavigationUtil.LIST_PAGE_SIZE, PageNavigationUtil.LIST_PAGE_SIZE); i++) {
            Shop shop = shops.get(keys.get(i + (getPage() - 1) * PageNavigationUtil.LIST_PAGE_SIZE));
            ItemStack item;
            if (mode.equals(ShopMode.EDIT))
                item = getDisplayItem(
                        shop.isLock(),
                        shop.getTrades().isEmpty() ? new ItemStack(Material.BARRIER) : shop.getTrades().get(0).getGiveItems()[0],
                        shop.getDisplayNameOrElseNone(),
                        ChatColor.YELLOW + LanguageKey.ITEM_LOCATION.getMessage(shop.getID()),
                        ChatColor.YELLOW + LanguageKey.ITEM_EDITOR_IS_SEARCHABLE.getMessage((shop.isSearchable() ? ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SEARCHABLE.getMessage() : ChatColor.RED + LanguageKey.ITEM_EDITOR_UNSEARCHABLE.getMessage())),
                        ChatColor.YELLOW + LanguageKey.ITEM_IS_LOCKED.getMessage((shop.isLock() ? ChatColor.RED + LanguageKey.ITEM_EDITOR_LOCKED.getMessage() : ChatColor.GREEN + LanguageKey.ITEM_EDITOR_UNLOCKED.getMessage())),
                        ChatColor.GREEN + LanguageKey.ITEM_LORE_CLICK_TO_OPEN.getMessage(),
                        ChatColor.GREEN + LanguageKey.ITEM_LORE_CLICK_TO_EDIT.getMessage()
                );
            else
                item = getDisplayItem(
                        shop.isLock(),
                        shop.getTrades().isEmpty() ? new ItemStack(Material.BARRIER) : shop.getTrades().get(0).getGiveItems()[0],
                        shop.getDisplayNameOrElseNone()
                );
            item = NBTUtil.setNMSTag(item, "Shop", shop.getID());
            inv.setItem(i, item);
        }
        PageNavigationUtil.setNavigationItems(inv, getPage(), maxPage);

        return inv;
    }


    private ItemStack getDisplayItem(boolean lock, ItemStack item, String name, String... lore) {
        return ItemUtil.getNamedItem(item, name, lock, lore);
    }
}
