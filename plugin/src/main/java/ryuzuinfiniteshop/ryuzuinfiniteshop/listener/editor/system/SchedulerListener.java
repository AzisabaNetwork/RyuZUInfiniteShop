package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.SearchTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ScheduleStringData;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SchedulerListener implements Listener {
    private static final HashMap<UUID, ScheduleStringData> schedulers = new HashMap<>();
    private static final HashMap<UUID, BukkitTask> searchSchedulers = new HashMap<>();

    public static void setSchedulers(Player p, String id, Inventory inv, Consumer<String> successProcess) {
        schedulers.put(p.getUniqueId(), new ScheduleStringData(System.currentTimeMillis(), id, inv, successProcess));
        ModeHolder holder = ShopUtil.getModeHolder(p.getOpenInventory().getTopInventory());
        if (holder != null) holder.setBefore(null);
        Bukkit.getScheduler().runTaskLater(RyuZUInfiniteShop.getPlugin(), p::closeInventory, 1L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void change(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (!schedulers.containsKey(p.getUniqueId())) return;
        if (FileUtil.isSaveBlock(p)) return;
        ScheduleStringData data = schedulers.get(p.getUniqueId());
        Shop shop = ShopUtil.getShop(data.getId());
        if (shop == null && !data.getId().equalsIgnoreCase("ignore")) return;
        event.setCancelled(true);
        if ((System.currentTimeMillis() - schedulers.get(p.getUniqueId()).getTime()) / 1000d > 20 || event.getMessage().equalsIgnoreCase("Cancel")) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_ENTER_CANCELLED.getMessage());
            SoundUtil.playClickShopSound(p);
            Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> p.openInventory(data.getInventory()));
        } else {
            if (shop == null) {
                if (data.getId().equals("ignore"))
                    Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> data.getSuccessProcess().accept(event.getMessage()));
                else {
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_FOUND.getMessage());
                    SoundUtil.playFailSound(p);
                }
            } else
                Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> data.getSuccessProcess().accept(event.getMessage()));
        }
        schedulers.remove(p.getUniqueId());
    }

    public static void setSearchScheduler(Player p, Supplier<LinkedHashMap<ShopTrade, Shop>> supplier, ShopMode mode, ModeHolder holder) {
        if(searchSchedulers.containsKey(p.getUniqueId())) {
            SoundUtil.playCautionSound(p);
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SEARCH_CANCELLED.getMessage());
            searchSchedulers.get(p.getUniqueId()).cancel();
            searchSchedulers.remove(p.getUniqueId());
        }

        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(RyuZUInfiniteShop.getPlugin(), () -> {
            LinkedHashMap<ShopTrade, Shop> searchedTrades = supplier.get();
            Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> {
                if (searchedTrades.isEmpty()) {
                    p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SEARCH_NORESULTS.getMessage());
                    SoundUtil.playFailSound(p);
                    searchSchedulers.remove(p.getUniqueId());
                    return;
                }
                p.openInventory(new SearchTradeGui(1, p, searchedTrades).getInventory(mode, holder));
                SoundUtil.playClickShopSound(p);
                searchSchedulers.remove(p.getUniqueId());
            });
        });
        searchSchedulers.put(p.getUniqueId(), task);
    }
}
