package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopEditorGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopItemStorage;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LogUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

//ショップをアイテム化およびロードする
public class ConvartListener implements Listener {
    @EventHandler
    public void convert(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopEditorGui)) return;
        if (!holder.getMode().equals(ShopMode.EDIT)) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        Shop shop = holder.getShop();
        int slot = event.getSlot();
        if (slot != 5 * 9 + 7) return;

        //コンバート
        ItemStack item = shop.convertShopToItemStack();
        if (item == null) {
            SoundUtil.playFailSound(p);
            return;
        }
        if (ItemUtil.ableGive(p.getInventory(), item)) {
            p.getInventory().addItem(item);
            SoundUtil.playSuccessSound(p);
        } else
            SoundUtil.playFailSound(p);
    }

    //トレードをアイテム化する
    @EventHandler
    public void convertTradeOne(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopTradeGui)) return;
        if (!holder.getMode().equals(ShopMode.EDIT)) return;
        if (event.getClickedInventory() == null) return;
        int slot = event.getSlot();
        if (!((ShopTradeGui) holder.getGui()).isConvertSlot(slot)) return;

        Player p = (Player) event.getWhoClicked();
        ItemStack item = holder.getShop().convertShopToItemStack(event.getClickedInventory(), slot);
        //トレードをアイテム化する
        if (!event.isShiftClick()) return;
        if (ItemUtil.isAir(item)) {
            SoundUtil.playFailSound(p);
            return;
        }
        if (ItemUtil.ableGive(p.getInventory(), item)) {
            p.getInventory().addItem(item);
            SoundUtil.playSuccessSound(p);
        } else
            SoundUtil.playFailSound(p);
    }

    //トレードを読み込む
    @EventHandler
    public void loadTrades(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopEditorGui)) return;
        if (!holder.getMode().equals(ShopMode.EDIT)) return;
        if (event.getClickedInventory() == null) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        ClickType click = event.getClick();
        Shop shop = holder.getShop();
        ItemStack item = event.getCursor();
        String tag = NBTUtil.getNMSStringTag(item, "ShopType");
        int slot = event.getSlot();

        if (slot != 5 * 9 + 8) return;
        if ((!click.isRightClick() && !click.isLeftClick()) || click.isShiftClick()) return;
        ShopItemStorage.StoredShop storedShop = ShopItemStorage.load(NBTUtil.getNMSStringTag(item, "ShopItemId"));
        if (storedShop != null) {
            if (!shop.getShopType().equals(ShopType.TwotoOne) && !storedShop.getShopType().equals(shop.getShopType())) {
                SoundUtil.playFailSound(p);
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_MATCH.getMessage());
                return;
            }
            shop.addAllTrades(storedShop.getTrades());
            holder.getGui().reloadInventory(event.getClickedInventory());
            SoundUtil.playSuccessSound(p);
            return;
        }
        if (tag == null) {
            SoundUtil.playFailSound(p);
            return;
        }
        ShopType type = ShopType.valueOf(tag);
        if (!type.equals(ShopType.TwotoOne) && !type.equals(shop.getShopType())) {
            SoundUtil.playFailSound(p);
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_MATCH.getMessage());
            return;
        }

        //トレードを読み込む
        if ((click.isRightClick() || click.isLeftClick()) && !click.isShiftClick()) {
            boolean duplication = shop.loadTrades(item, p);

            //音を出す
            if (duplication) {
                SoundUtil.playCautionSound(p);
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_TRADE_DUPLICATE.getMessage());
            } else {
                SoundUtil.playSuccessSound(p);
            }

            //インベントリを更新する
            holder.getGui().reloadInventory(event.getClickedInventory());
        }

    }

    //ショップを読み込む
    @EventHandler
    public void loadShop(PlayerInteractEvent event) {
        //ショップ召喚用アイテムなのかチェック
        if (event.getHand() == null) return;
        if (!event.getHand().equals(EquipmentSlot.HAND)) return;
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;

        //必要なデータを取得
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();

        if (!p.hasPermission("sis.op")) return;
        if (!p.isSneaking()) return;
        String shopData = NBTUtil.getNMSStringTag(item, "ShopData");
        ShopItemStorage.StoredShop storedShop = ShopItemStorage.load(NBTUtil.getNMSStringTag(item, "ShopItemId"));
        if (storedShop != null) shopData = storedShop.getShopData();
        if (ItemUtil.isAir(item) || shopData == null) return;
        if (block == null) return;
        if (FileUtil.isSaveBlock(p)) return;

        //ショップを読み込む
        Shop shop = ShopUtil.reloadShop(block.getLocation().add(0, 1, 0), shopData,
                storedShop == null ? TradeUtil.convertTradesToList(item) : storedShop.getTrades());
        shop.respawnNPC();

        //音を出し、メッセージを送信
        SoundUtil.playSuccessSound(p);
        p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SUCCESS_SHOP_CREATE.getMessage(shop.getDisplayNameOrElseShop()));
        LogUtil.log(LogUtil.LogType.CREATESHOP, p.getName(), shop.getID());
    }

    //ショップをマージする
//    @EventHandler
//    public void mergeShop(PlayerInteractAtEntityEvent event) {
//        Entity entity = event.getRightClicked();
//        Player p = event.getPlayer();
//        if (!event.getHand().equals(EquipmentSlot.HAND)) return;
//        if (!p.hasPermission("sis.op")) return;
//        if (p.isSneaking()) return;
//        String id = NBTUtil.getNMSStringTag(entity, "Shop");
//        if (id == null) return;
//        Shop shop = ShopUtil.getShop(id);
//        if (shop.isEditting()) return;
//        if(FileUtil.isSaveBlock(p)) return;
//        ItemStack item = p.getInventory().getItemInMainHand();
//        String shopData = NBTUtil.getNMSStringTag(item, "ShopData");
//        if (ItemUtil.isAir(item) || shopData == null) return;
//
//        String tag = NBTUtil.getNMSStringTag(item, "ShopType");
//        if (!(shop.getShopType().equals(ShopType.TwotoOne) || ShopType.valueOf(tag).equals(shop.getShopType()))) {
//            SoundUtil.playFailSound(p);
//            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_MATCH.getMessage());
//            return;
//        }
//
//        HashMap<String, String> data = ShopUtil.mergeShop(item, shop, p);
//        String displayName = shop.getDisplayNameOrElseShop();
//        item = NBTUtil.setNMSTag(item, data);
//        p.getInventory().setItemInMainHand(item);
//        p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SUCCESS_SHOP_MERGE.getMessage(displayName));
//        SoundUtil.playSuccessSound(p);
//    }
}
