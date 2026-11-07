package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

/**
 * Loads shops whose world was not loaded yet when the shop files were read
 * (e.g. worlds loaded later by a world-management plugin), and sets shops
 * aside again when their world unloads.
 */
public class WorldLoadShopListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (Shop shop : ShopUtil.loadDeferredShops(event.getWorld().getName())) {
            shop.respawnNPC();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        ShopUtil.unloadShops(event.getWorld().getName());
    }
}
