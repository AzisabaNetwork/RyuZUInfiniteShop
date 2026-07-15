package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.DisplayPanelConfig;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public abstract class ShopTradeGui extends ShopGui {

    public ShopTradeGui(Shop shop, int page) {
        super(shop, page);
    }

    public abstract List<Integer> getDisplaySlot();

    public abstract List<Integer> getConvertSlot();

    public boolean isConvertSlot(int slot) {
        return getConvertSlot().contains(slot);
    }

    public boolean isDisplaySlot(int slot) {
        return getDisplaySlot().contains(slot);
    }

    public ShopTrade getTrade(int number) {
        if (getTrades().size() <= number) return null;
        if (number < 0) return null;
        return getTrades().get(number);
    }

    public Inventory getInventory(Function<Integer, Integer> function, ShopMode mode) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(mode, getShop(), this), 9 * 6, PageNavigationUtil.title(getShop().getDisplayNameOrElseShop(), getPage(), getShop().getPageCount()));

        for (int i = 0; i < getTrades().size(); i++) {
            ShopTrade trade = getTrades().get(i);
            int slot = function.apply(i);
            ItemStack[] items = trade.getTradeItems(getShop().getShopType(), mode, mode.equals(ShopMode.TRADE));
            for (int k = 0; k < items.length; k++) {
                inv.setItem(slot + k, items[k]);
            }
        }

        for (int i = function.apply(getTrades().size()); i < PageNavigationUtil.PREVIOUS_SLOT; i++) {
            if (mode.equals(ShopMode.TRADE)) inv.setItem(i, ShopTrade.getFilter());
        }
        PageNavigationUtil.setNavigationItems(inv, getPage(), getShop().getPageCount());

        return inv;
    }

    public Inventory getInventory(ShopMode mode, Player p) {
        Inventory inv = getInventory(mode);
        if (mode.equals(ShopMode.TRADE)) setTradeStatus(p, inv);
        return inv;
    }

    public Inventory getInventory(ShopMode mode, Player p, @Nullable ModeHolder before) {
        Inventory inv = getInventory(mode);
        ((ShopHolder) inv.getHolder()).setBefore(before);
        if (mode.equals(ShopMode.TRADE)) setTradeStatus(p, inv);
        return inv;
    }

    protected ItemStack getTradePanel(int i, ShopMode mode) {
        return (getTrades().size() - 1) >= i ? getTrades().get(i).getFilter(mode) : ShopTrade.getFilterNoData(mode);
    }

    public boolean existTrades() {
        return getTrades().size() > (getPage() - 2) * getShop().getShopType().getLimitSize();
    }

    public abstract ShopTrade getTradeFromSlot(int slot);

    public void setTradeStatus(Player p, Inventory inventory) {
        for (int i = 0; i < getTrades().size(); i++) {
            ShopTrade trade = getTrade(i);
            ShopTrade.TradeResult result = trade.getResult(p, shop);
            inventory.setItem(getConvertSlot().get(i), NBTUtil.setNMSTag(trade.getOption().getOptionsPanel(DisplayPanelConfig.getPanel(result).getItemStack(), p, trade), "A", String.valueOf(i)));
        }
    }
}
