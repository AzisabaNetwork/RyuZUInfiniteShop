package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ModeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;

@NoArgsConstructor
@AllArgsConstructor
public class SelectSearchItemGui extends ModeGui {
    @Getter
    private static final ItemStack panel = ItemUtil.getNamedItem(
            ItemUtil.getColoredItem("WHITE_STAINED_GLASS_PANE"),
            ChatColor.BLUE + LanguageKey.ITEM_SEARCH_INFO.getMessage(),
            ChatColor.YELLOW + LanguageKey.ITEM_SEARCH_REGISTER.getMessage(),
            ChatColor.YELLOW + LanguageKey.ITEM_LORE_SEARCH_BY_NPC.getMessage()
    );
    @Getter
    @Setter
    protected ItemStack searchItem;

    public Inventory getInventory(ShopMode mode) {
        Inventory inv = Bukkit.createInventory(new ModeHolder(ShopMode.SEARCH, this), 9, ChatColor.DARK_BLUE + LanguageKey.INVENTORY_SEARCH_TRADE.getMessage());

        for (int i = 1; i < 8; i++) inv.setItem(i, ShopTrade.getFilter());
        inv.setItem(
                0,
                ItemUtil.getNamedItem(
                        ItemUtil.getColoredItem("RED_STAINED_GLASS_PANE"),
                        ChatColor.RED + LanguageKey.ITEM_SEARCH_BY_VALUE.getMessage(),
                        ChatColor.YELLOW + LanguageKey.ITEM_LORE_SEARCH_BY_ITEM.getMessage(),
                        ChatColor.YELLOW + LanguageKey.ITEM_LORE_SEARCH_BY_NAME.getMessage()
                )
        );
        inv.setItem(
                8,
                ItemUtil.getNamedItem(
                        ItemUtil.getColoredItem("GREEN_STAINED_GLASS_PANE"),
                        ChatColor.GREEN + LanguageKey.ITEM_SEARCH_BY_PRODUCT.getMessage(),
                        ChatColor.YELLOW + LanguageKey.ITEM_LORE_SEARCH_BY_ITEM.getMessage(),
                        ChatColor.YELLOW + LanguageKey.ITEM_LORE_SEARCH_BY_NAME.getMessage()
                )
        );
        inv.setItem(4, ItemUtil.isAir(searchItem) ? panel : searchItem);

        return inv;
    }
}
