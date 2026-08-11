package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.canceller;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.MerchantInventory;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LocationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

public class CancelAffectNpc implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void cancelDamage(EntityDamageEvent e) {
        cancel(e, e.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cancelBurn(EntityCombustEvent e) {
        cancel(e, e.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cancelBurn(CreeperPowerEvent e) {
        cancel(e, e.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void bypassSpawn(EntitySpawnEvent e) {
        if(EntityUtil.entityQueue.isEmpty()) return;
        if(!EntityUtil.entityQueue.peek().contains(e)) return;
        e.setCancelled(false);
        EntityUtil.entityQueue.poll();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cancelVillagerTradeInventory(InventoryOpenEvent e) {
        if(!(e.getInventory() instanceof MerchantInventory)) return;
        if(!(e.getInventory().getHolder() instanceof AbstractVillager)) return;
        AbstractVillager villager = (AbstractVillager) e.getInventory().getHolder();
        if(ShopUtil.getShop(LocationUtil.toStringFromLocation(villager.getLocation())) == null) return;
        e.setCancelled(true);
    }

    private void cancel(Cancellable e, Entity entity) {
        String id = NBTUtil.getNMSStringTag(entity, "Shop");
        if (id == null) return;
        e.setCancelled(true);
    }
}
