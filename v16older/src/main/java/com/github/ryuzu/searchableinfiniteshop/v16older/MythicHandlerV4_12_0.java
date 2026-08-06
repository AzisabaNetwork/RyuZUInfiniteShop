package com.github.ryuzu.searchableinfiniteshop.v16older;

import com.github.ryuzu.searchableinfiniteshop.api.IMythicHandler;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import io.lumine.xikage.mythicmobs.api.exceptions.InvalidMobTypeException;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;


public class MythicHandlerV4_12_0 implements IMythicHandler, Listener {
    private static final HashMap<ItemStack, String> items = new HashMap<>();
    private final JavaPlugin plugin;
    private final Consumer<Runnable> reloadProcessor;

    public MythicHandlerV4_12_0(JavaPlugin plugin, Consumer<Runnable> reloadProcessor) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.reloadProcessor = reloadProcessor;
    }

    @EventHandler
    public void onReload(MythicReloadedEvent event) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> reload(reloadProcessor));
        } else {
            reload(reloadProcessor);
        }
    }

    @Override
    public void reload(Consumer<Runnable> consumer) {
        consumer.accept(() -> {
            items.clear();
            items.putAll(getMythicMobsInstance().getItemManager().getItems().stream().collect(
                    Collectors.toMap(
                            item -> BukkitAdapter.adapt(item.generateItemStack(1)),
                            MythicItem::getInternalName,
                            (existing, replacement) -> existing
                    )
            ));
        });
    }

    @Override
    public String getID(ItemStack item) {
        if (item == null || item.getType().equals(Material.AIR)) return null;
        ItemStack copy = item.clone();
        copy.setAmount(1);
        String id = getMythicMobsInstance().getVolatileCodeHandler().getItemHandler().getNBTData(copy).getString("MYTHIC_TYPE");
        return id == null || id.isEmpty() ? items.get(copy) : id;
    }

    @Override
    public boolean exsistsMythicMob(String id) {
        return getMythicMobsInstance().getAPIHelper().getMythicMob(id) != null;
    }

    @Override
    public Collection<String> getMythicMobs() {
        return getMythicMobsInstance().getMobManager().getMobNames();
    }

    @Override
    public ItemStack getMythicItem(String id, int amount) {
        ItemStack item = getMythicMobsInstance().getItemManager().getItemStack(id);
        if (item == null) return null;
        item.setAmount(amount);
        return item;
    }

    @Override
    public Entity spawnMythicMob(Location location, String id) {
        try {
            return getMythicMobsInstance().getAPIHelper().spawnMythicMob(id, location);
        } catch (InvalidMobTypeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            getMythicMobsInstance().getLogger().log(
                    Level.WARNING,
                    "Failed to spawn MythicMob shop NPC (MMID: " + id + ", location: " + location + ")",
                    e
            );
            return null;
        }
    }

    @Override
    public EntityType getEntityType(String id) {
        if(!exsistsMythicMob(id)) return null;
        return EntityType.valueOf(getMythicMobsInstance().getMobManager().getMythicMob(id).getEntityType().toUpperCase());
    }

    @Override
    public boolean isMythicMob(Entity entity) {
        return getMythicMobsInstance().getAPIHelper().isMythicMob(entity);
    }

    @Override
    public JavaPlugin getPlugin() {
        return getMythicMobsInstance();
    }

    private MythicMobs getMythicMobsInstance() {
        return MythicMobs.inst();
    }
}
