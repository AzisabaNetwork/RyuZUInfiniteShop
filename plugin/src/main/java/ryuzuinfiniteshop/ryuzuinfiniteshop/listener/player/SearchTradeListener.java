package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.player;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.SearchTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.SelectSearchItemGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.ShopListGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.SeachTradeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system.SchedulerListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

public class SearchTradeListener implements Listener {
    @EventHandler
    public void selectSearchItem(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ModeHolder holder = ShopUtil.getModeHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof SelectSearchItemGui)) return;
        if (event.getClickedInventory() == null) return;

        event.setCancelled(true);
        //必要なデータを取得

        Player p = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ClickType type = event.getClick();
        SelectSearchItemGui gui = (SelectSearchItemGui) holder.getGui();
        if (slot != 0 && slot != 4 && slot != 8) return;

        ShopMode mode = p.hasPermission("sis.op") ? ShopMode.EDIT : ShopMode.SEARCH;

        if ((type.isRightClick() || type.isLeftClick())) {
            if (slot == 4) {
                if (event.isShiftClick()) {
                    // NPC名で検索
                    SchedulerListener.setSchedulers(p, "ignore", event.getClickedInventory(), (message) -> {
                        p.openInventory(new ShopListGui(1, ShopUtil.getSortedShops(mode, message)).getInventory(mode, holder));
                        SoundUtil.playClickShopSound(p);
                    });
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SEARCH_NPC.getMessage());
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_CANCEL.getMessage());

                } else if (ItemUtil.isAir(event.getCursor()))
                    event.setCurrentItem(SelectSearchItemGui.getPanel());
                else
                    event.setCurrentItem(ItemUtil.getOneItemStack(event.getCursor()));
                gui.setSearchItem(event.getCurrentItem());
                SoundUtil.playClickShopSound(p);
            } else {
                if (event.isShiftClick()) {
                    // 対価名、商品名で検索
                    SchedulerListener.setSchedulers(p, "ignore", event.getClickedInventory(), (message) -> SchedulerListener.setSearchScheduler(p, () -> slot == 0 ? TradeUtil.getTradesFromTakeByDisplayName(message, mode) : TradeUtil.getTradesFromGiveByDisplayName(message, mode), mode, holder));
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_SEARCH_PROMPT.getMessage());
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_CANCEL.getMessage());

                } else {
                    if (ItemUtil.isAir(gui.getSearchItem()))
                        SoundUtil.playFailSound(p);
                    else {
                        // アイテムで検索
                        SchedulerListener.setSearchScheduler(p, () -> slot == 0 ? TradeUtil.getTradesFromTake(gui.getSearchItem(), mode) : TradeUtil.getTradesFromGive(gui.getSearchItem(), mode), mode, holder);
                    }
                }
            }
        }
    }

    @EventHandler
    public void changePage(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ModeHolder holder = ShopUtil.getModeHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof SearchTradeGui)) return;
        ItemStack clickedItem = event.getCurrentItem();
        boolean previousButton = PageNavigationUtil.isPrevious(clickedItem);
        boolean nextButton = PageNavigationUtil.isNext(clickedItem);
        if (event.getClickedInventory() != null && !(previousButton || nextButton)) return;

        //必要なデータを取得
        SeachTradeHolder seachTradeHolder = (SeachTradeHolder) holder;
        Player p = (Player) event.getWhoClicked();
        ClickType type = event.getClick();
        ShopMode mode = holder.getMode();
        int page = seachTradeHolder.getGui().getPage();

        //ページ切り替え
        boolean fail = false;
        if (previousButton || (event.getClickedInventory() == null && type.isLeftClick())) {
            if (page - 1 == 0)
                fail = true;
            else
                p.openInventory(new SearchTradeGui(page - 1, p, seachTradeHolder.getTrades()).getInventory(mode, holder.getBefore()));
        }
        if (nextButton || (event.getClickedInventory() == null && type.isRightClick())) {
            if (page == seachTradeHolder.getMaxPage()) {
                fail = true;
            } else
                p.openInventory(new SearchTradeGui(page + 1, p, seachTradeHolder.getTrades()).getInventory(mode, holder.getBefore()));
        }
        if (fail) {
            SoundUtil.playFailSound(p);
        } else {
            SoundUtil.playClickShopSound(p);
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void openShop(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ModeHolder holder = ShopUtil.getModeHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof SearchTradeGui)) return;
        if (event.getClickedInventory() == null) return;
        if (PageNavigationUtil.isPrevious(event.getCurrentItem()) || PageNavigationUtil.isNext(event.getCurrentItem())) return;

        event.setCancelled(true);

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        Inventory inv = event.getView().getTopInventory();
        int slot = event.getSlot();
        int base = (event.getSlot() / 9) * 9;
        int info = NBTUtil.getNMSStringTag(inv.getItem(4 + base), "Shop") == null ? 6 : 4;
        ItemStack item = inv.getItem(info + base);
        Shop shop = ShopUtil.getShop(NBTUtil.getNMSStringTag(item, "Shop"));
        boolean isOpenShop = slot % 9 == info;
        if (shop == null && isOpenShop) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_NOT_FOUND.getMessage());
            SoundUtil.playFailSound(p);
            return;
        }

        if (isOpenShop) {
            if (p.hasPermission("sis.op") && event.isShiftClick()) {
                //編集画面を開く
                ShopUtil.closeAllShopTradeInventory(shop);
                p.openInventory(shop.getEditor(1).getInventory(ShopMode.EDIT, holder));
                SoundUtil.playClickShopSound(p);
                shop.setEditting(true);
            } else {
                //取引画面を開く
                if (!shop.isOpenableShop(p)) return;
                ShopTradeGui gui = shop.getPage(Integer.parseInt(NBTUtil.getNMSStringTag(item, "Page")));
                if (gui == null) {
                    SoundUtil.playFailSound(p);
                    return;
                }
                p.openInventory(gui.getInventory(ShopMode.TRADE, p, holder));
                SoundUtil.playClickShopSound(p);
            }
        } else {
            if (ItemUtil.isAir(event.getCurrentItem())) return;
            if (!(p.hasPermission("sis.search") || p.hasPermission("sis.op"))) return;
            if (slot % 9 == info) return;
            SchedulerListener.setSearchScheduler(p, () -> (slot % 9 < info) ? TradeUtil.getTradesFromGive(event.getCurrentItem(), holder.getMode()) : TradeUtil.getTradesFromTake(event.getCurrentItem(), holder.getMode()), holder.getMode(), holder);
        }
    }

    //取引、対価で検索を行う
    @EventHandler
    public static void search(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopTradeGui)) return;
        if (!holder.getMode().equals(ShopMode.TRADE)) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        ShopType type = holder.getShop().getShopType();
        int slot = event.getSlot();
        ShopTrade trade = ((ShopTradeGui) holder.getGui()).getTradeFromSlot(slot);

        if (trade == null) return;
        if (!(p.hasPermission("sis.search") || p.hasPermission("sis.op"))) return;
//        if (trade.getResult(p, holder.getShop()).equals(ShopTrade.TradeResult.Success)) return;

        int info = type.getSubtractSlot();
        int surplus = slot % 9;
        int base = (type.equals(ShopType.TwotoOne) && surplus > 4) ? surplus - 5 : surplus;
        if (ItemUtil.isAir(event.getCurrentItem())) return;
        if (base == info) return;
        ShopMode mode = p.hasPermission("sis.op") ? ShopMode.EDIT : ShopMode.SEARCH;

        ItemStack item = trade.getTradeItems(holder.getShop().getShopType(), mode)[base];
        SchedulerListener.setSearchScheduler(p, () -> base < info ? TradeUtil.getTradesFromGive(item, mode) : TradeUtil.getTradesFromTake(item, mode), mode, holder);
    }
}
