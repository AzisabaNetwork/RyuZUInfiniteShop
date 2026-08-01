package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.UnderstandSystemConfig;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.CitizensHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LocationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.util.HashMap;

public class OpenShopListener implements Listener {
    //ショップを開く
    @EventHandler
    public void openShop(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player p = event.getPlayer();
        if (entity instanceof LivingEntity && !entity.getType().equals(EntityType.ARMOR_STAND) && !entity.getType().equals(EntityType.ENDER_DRAGON))
            return;
        if (!event.getHand().equals(EquipmentSlot.HAND)) return;
        String id = JavaUtil.getNonNull(CitizensHandler.getCitizensShopString(entity), NBTUtil.getNMSStringTag(entity, "Shop"));
        if (id == null) return;
        event.setCancelled(true);
        openShop(id, p);
    }

    //ショップを開く
    @EventHandler
    public void openShop(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player p = event.getPlayer();
        if (!(entity instanceof LivingEntity)) return;
        if (!event.getHand().equals(EquipmentSlot.HAND)) return;
        String id = JavaUtil.getNonNull(CitizensHandler.getCitizensShopString(entity), NBTUtil.getNMSStringTag(entity, "Shop"));
        if (id == null) return;
        event.setCancelled(true);
        openShop(id, p);
    }

    @EventHandler
    public void openShop(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() == null) return;
        if (!event.getHand().equals(EquipmentSlot.HAND)) return;
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String id = LocationUtil.toStringFromLocation(block.getLocation());
        if (ShopUtil.getShop(id) == null) return;
        event.setCancelled(true);
        openShop(id, p);
    }

    public static void openShop(String id, Player p) {
        if (FileUtil.isSaveBlock(p)) return;
        Shop shop = ShopUtil.getShop(id);
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!(ItemUtil.isAir(item) || NBTUtil.getNMSStringTag(item, "ShopData") == null)) {
            // ショップのマージ
            if (!p.hasPermission("sis.op")) return;
            if (shop.isEditting(p)) return;
            ShopType type = ShopType.valueOf(NBTUtil.getNMSStringTag(item, "ShopType"));
            if (!(shop.getShopType().equals(ShopType.TwotoOne) || type.equals(shop.getShopType()))) {
                SoundUtil.playFailSound(p);
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_MATCH.getMessage());
                return;
            }

            HashMap<String, String> data = ShopUtil.mergeShop(item, shop, p);
            String displayName = shop.getDisplayNameOrElseShop();
            item = NBTUtil.setNMSTag(item, data);
            p.getInventory().setItemInMainHand(item);
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SUCCESS_SHOP_MERGE.getMessage(displayName + ChatColor.GREEN));
            SoundUtil.playSuccessSound(p);
        } else {
            if (p.isSneaking() && p.hasPermission("sis.op")) {
                //ショップの編集画面を開く
                if (shop.isEditting(p)) return;
                ShopUtil.closeAllShopTradeInventory(shop);
                p.openInventory(shop.getEditor(1).getInventory(ShopMode.EDIT));
                shop.setEditting(true);
            } else {
                //ショップの取引画面を開く
                if (!shop.isOpenableShop(p)) return;
                if (!UnderstandSystemConfig.contains(p)) {
                    TextComponent understand = new TextComponent(ChatColor.YELLOW + "[" + LanguageKey.MESSAGE_UNDERSTAND_BUTTON_MESSAGE.getMessage() + ChatColor.YELLOW + "]");
                    understand.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sis understand"));
                    understand.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.GREEN + LanguageKey.MESSAGE_UNDERSTAND_BUTTON_TOOLTIP.getMessage()).create()));
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_PAGE_NAVIGATION.getMessage());
                    p.spigot().sendMessage(understand);
                }
                p.openInventory(shop.getPage(1).getInventory(ShopMode.TRADE, p));
            }
        }
    }

    //ショップのページ切り替え
    @EventHandler
    public void changePage(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopTradeGui)) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        ClickType type = event.getClick();
        ItemStack clickedItem = event.getCurrentItem();
        boolean previousButton = PageNavigationUtil.isPrevious(clickedItem);
        boolean nextButton = PageNavigationUtil.isNext(clickedItem);
        if (event.getClickedInventory() != null && !(previousButton || nextButton)) return;
        Inventory inv = event.getView().getTopInventory();
        ShopMode mode = holder.getMode();
        Shop shop = holder.getShop();
        int page = holder.getGui().getPage();

        //ページ切り替え
        boolean fail = false;
        if (previousButton || (event.getClickedInventory() == null && type.isLeftClick())) {
            if (shop.getPage(page - 1) == null)
                fail = true;
            else
                p.openInventory(shop.getPage(page - 1).getInventory(mode, p, holder.getBefore()));
        }
        if (nextButton || (event.getClickedInventory() == null && type.isRightClick())) {
            if (shop.getPage(page + 1) == null) {
                fail = true;
                if (holder.getMode().equals(ShopMode.EDIT)) {
                    //取引を上書きし、取引として成立しないものは削除する
                    boolean warn = shop.checkTrades(inv, p);
                    if (warn) {
                        p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_TRADE_DUPLICATE.getMessage());
                        p.openInventory(shop.getPage(page).getInventory(mode, p, holder.getBefore()));
                    } else if (shop.ableCreateNewPage()) {
                        shop.createNewPage();
                        if (shop.getPage(page + 1) != null) {
                            p.openInventory(shop.getPage(page + 1).getInventory(mode, p, holder.getBefore()));
                            fail = false;
                        }
                    }
                }
            } else
                p.openInventory(shop.getPage(page + 1).getInventory(mode, p, holder.getBefore()));
        }
        if (fail)
            SoundUtil.playFailSound(p);
        else
            SoundUtil.playClickShopSound(p);

        //イベントキャンセル
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void openBeforePage(InventoryCloseEvent event) {
        //インベントリがショップなのかチェック
        Inventory inv = event.getInventory();
        ModeHolder holder = ShopUtil.getModeHolder(inv);
        if (holder == null) return;

        //必要なデータを取得
        Player p = (Player) event.getPlayer();
        if (FileUtil.isSaveBlock(p)) return;

        Bukkit.getScheduler().runTaskLater(RyuZUInfiniteShop.getPlugin(), () -> {
            if (p.getOpenInventory().getType().equals(InventoryType.CREATIVE) || p.getOpenInventory().getType().equals(InventoryType.CRAFTING))
                SoundUtil.playCloseShopSound(p);
            if (ShopUtil.getModeHolder(p.getOpenInventory().getTopInventory()) != null) return;
            if (holder.getBefore() == null) return;
            if (holder.getBefore().getGui() instanceof ShopTradeGui)
                p.openInventory(((ShopTradeGui) holder.getBefore().getGui()).getInventory(holder.getBefore().getMode(), p, holder.getBefore().getBefore()));
            else
                p.openInventory(holder.getBefore().getGui().getInventory(holder.getBefore().getMode(), holder.getBefore().getBefore()));
        }, 1L);
    }

    //ショップのステータスの更新
    @EventHandler
    public void updateStatus(InventoryDragEvent event) {
        updateStatusProcess(event);
    }

    @EventHandler
    public void updateStatus(InventoryClickEvent event) {
        updateStatusProcess(event);
    }

    private void updateStatusProcess(InventoryInteractEvent event) {
        ShopHolder holder = ShopUtil.getShopHolder(event.getView().getTopInventory());
        if (holder == null) return;
        if (!holder.getMode().equals(ShopMode.TRADE)) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        Inventory inv = event.getView().getTopInventory();

        Bukkit.getScheduler().runTaskLater(RyuZUInfiniteShop.getPlugin(), () -> ((ShopTradeGui) holder.getGui()).setTradeStatus(p, inv), 1L);
    }
}
