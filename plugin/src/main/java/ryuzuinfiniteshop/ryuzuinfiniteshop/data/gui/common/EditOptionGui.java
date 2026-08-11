package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.DisplayPanelConfig;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.OptionHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.OptionType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.VaultHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.XMaterial;

import java.util.Arrays;
import java.util.List;

@Getter
public class EditOptionGui extends ShopGui {
    protected final ShopTrade trade;
    protected final int slot;

    public EditOptionGui(ShopTrade trade, Shop shop, int page, int slot) {
        super(shop, page);
        this.trade = trade;
        this.slot = slot;
    }

    @Override
    public Inventory getInventory(ShopMode mode) {
        Inventory inv = Bukkit.createInventory(new OptionHolder(ShopMode.EDIT, shop, this), 9 * (VaultHandler.isLoaded() ? 4 : 3), ChatColor.DARK_BLUE + LanguageKey.INVENTORY_EDITOR_OPTIONS.getMessage());

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, trade.getTradeItems(shop.getShopType())[i]);
        }
        inv.setItem(shop.getShopType().equals(ShopType.SixtoTwo) ? 6 : 4, ItemUtil.getNamedItem(DisplayPanelConfig.getPanel(ShopTrade.TradeResult.Normal).getItemStack(), ChatColor.BLACK + ""));

        //取引上限
        List<String> limits = Arrays.asList("-64", "-32", "-8", "-1", "0", "+1", "+8", "+32", "+64");
        for (int i = 0; i < 9; i++) {
            ItemStack panel = ItemUtil.getNamedItem(ItemUtil.getColoredItem((i < 5 ? "RED" : "GREEN") + "_STAINED_GLASS_PANE"), (i < 5 ? ChatColor.RED : ChatColor.GREEN) + limits.get(i));
            panel = NBTUtil.setNMSTag(panel, "OptionType", "Limit");
            panel = NBTUtil.setNMSTag(panel, "OptionValue", limits.get(i).replace("+", ""));
            inv.setItem(i + 9, panel);
        }
        inv.setItem(13, getOptionPanel(OptionType.LIMIT));

        //取引確率
        List<String> rates = Arrays.asList("-100", "-30", "-10", "-1", "0", "+1", "+30", "+50", "+100");
        for (int i = 0; i < 9; i++) {
            ItemStack panel = ItemUtil.getNamedItem(ItemUtil.getColoredItem((i < 5 ? "RED" : "GREEN") + "_STAINED_GLASS_PANE"), (i < 5 ? ChatColor.RED : ChatColor.GREEN) + rates.get(i));
            panel = NBTUtil.setNMSTag(panel, "OptionType", "Rate");
            panel = NBTUtil.setNMSTag(panel, "OptionValue", rates.get(i).replace("+", ""));
            inv.setItem(i + 9 * 2, panel);
        }
        inv.setItem(22, getOptionPanel(OptionType.RATE));

        //お金
        if (VaultHandler.isLoaded()) {
            List<String> money = Arrays.asList("-1,000,000", "-10,000", "-100", "-1", "0", "+1", "+100", "+10,000", "+1,000,000");
            for (int i = 0; i < 9; i++) {
                ItemStack panel = ItemUtil.getNamedItem(ItemUtil.getColoredItem((i < 5 ? "RED" : "GREEN") + "_STAINED_GLASS_PANE"), (i < 5 ? ChatColor.RED : ChatColor.GREEN) + money.get(i));
                panel = NBTUtil.setNMSTag(panel, "OptionType", "Money");
                panel = NBTUtil.setNMSTag(panel, "OptionValue", money.get(i).replace("+", "").replace(",", ""));
                inv.setItem(i + 9 * 3, panel);
            }
            inv.setItem(31, getOptionPanel(OptionType.MONEY));
        }

        return inv;
    }

    public ItemStack getOptionPanel(OptionType type) {
        switch (type) {
            case LIMIT:
                return NBTUtil.setNMSTag(ItemUtil.getNamedItem(Material.BARRIER, ChatColor.BLUE + LanguageKey.ITEM_OPTIONS_LIMIT_VALUE_EDITTING.getMessage(ChatColor.YELLOW + String.valueOf(trade.getOption().getLimit())), ChatColor.YELLOW + LanguageKey.ITEM_OPTIONS_LIMIT_SHIFT.getMessage()), "OptionType", "Limit");
            case RATE:
                return NBTUtil.setNMSTag(ItemUtil.getNamedItem(XMaterial.matchXMaterial("DAMAGED_ANVIL").get().parseMaterial(), ChatColor.BLUE + LanguageKey.ITEM_OPTIONS_RATE_VALUE.getMessage(ChatColor.YELLOW + String.valueOf(trade.getOption().getRate()), (trade.getOption().isHide() ? ChatColor.RED + LanguageKey.ITEM_OPTIONS_RATE_HIDE.getMessage() : ChatColor.GREEN + LanguageKey.ITEM_OPTIONS_RATE_SHOW.getMessage())), ChatColor.YELLOW + LanguageKey.ITEM_OPTIONS_RATE_CLICK.getMessage(), ChatColor.YELLOW + LanguageKey.ITEM_OPTIONS_RATE_SHIFT.getMessage()), "OptionType", "Rate");
            case MONEY:
                return NBTUtil.setNMSTag(
                        ItemUtil.getNamedItem(Material.GOLD_INGOT, ChatColor.BLUE + LanguageKey.ITEM_OPTIONS_MONEY_VALUE.getMessage(ChatColor.YELLOW + String.valueOf(VaultHandler.getInstance().format(trade.getOption().getMoney())), (trade.getOption().isGive() ? ChatColor.GREEN + LanguageKey.ITEM_OPTIONS_MONEY_RECEIVE.getMessage() : ChatColor.RED + LanguageKey.ITEM_OPTIONS_MONEY_PAY.getMessage())), ChatColor.YELLOW + LanguageKey.ITEM_OPTIONS_MONEY_CLICK.getMessage(), ChatColor.YELLOW + LanguageKey.ITEM_OPTIONS_MONEY_SHIFT.getMessage()), "OptionType", "Money");
        }
        return new ItemStack(Material.AIR);
    }
}
