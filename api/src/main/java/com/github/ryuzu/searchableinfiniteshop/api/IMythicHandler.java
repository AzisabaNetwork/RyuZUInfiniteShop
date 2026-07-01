package com.github.ryuzu.searchableinfiniteshop.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.function.Consumer;


public interface IMythicHandler {
    String getID(ItemStack item);

    void reload(Consumer<Runnable> consumer);

    boolean existsMythicMob(String id);

    Collection<String> getMythicMobs();

    ItemStack getMythicItem(String id, int amount);

    Entity spawnMythicMob(Location location, String id);

    EntityType getEntityType(String id);

    boolean isMythicMob(Entity entity);

    JavaPlugin getPlugin();
}
