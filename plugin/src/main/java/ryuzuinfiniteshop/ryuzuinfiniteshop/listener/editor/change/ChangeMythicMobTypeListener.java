package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.change;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopEditorGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system.SchedulerListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

//ショップのNPCの名前を変更する
public class ChangeMythicMobTypeListener implements Listener {
    @EventHandler
    public void changeMythicMobType(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopEditorGui)) return;
        if (!holder.getMode().equals(ShopMode.EDIT)) return;
        if (event.getClickedInventory() == null) return;
        if (ItemUtil.getWhitePanel().equals(event.getCurrentItem())) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        Shop shop = holder.getShop();
        int slot = event.getSlot();
        if (slot != 4 * 9 + 3) return;
        //チャット入力待機
        SchedulerListener.setSchedulers(p, shop.getID(), event.getClickedInventory(), (message) -> {
            //成功時の処理
            //NPCを再構築する
            if (!MythicInstanceProvider.getInstance().existsMythicMob(message)) {
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.COMMAND_INVALID_MYTHIC_MOB_ID.getMessage());
                SoundUtil.playFailSound(p);
                return;
            }
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SUCCESS_SET_MYTHIC_MOB_ID.getMessage());
            SoundUtil.playSuccessSound(p);
            shop.setMythicType(message);
            ShopUtil.reloadShop(shop);
            ShopUtil.getShop(shop.getID()).respawnNPC();
        });
        p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_MYTHICMOBID.getMessage());
        p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_CANCEL.getMessage());

        SoundUtil.playClickShopSound(p);
        holder.getShop().setEditting(false);
    }
}
