package net.azisaba.sis.item.mythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * The one place that talks to MythicMobs. Everything else (Kotlin code and the matchers carried over from 1.x) goes
 * through here, so the plugin keeps working when MythicMobs is not installed.
 */
public final class MythicInstanceProvider {
    private static JavaPlugin plugin;
    private static Handler instance;

    private MythicInstanceProvider() {
    }

    public static void init(JavaPlugin owner) {
        plugin = owner;
        instance = Bukkit.getPluginManager().getPlugin("MythicMobs") != null ? new Handler() : null;
    }

    public static JavaPlugin plugin() {
        return plugin;
    }

    public static boolean isLoaded() {
        return instance != null && Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public static Handler getInstance() {
        if (instance == null) throw new IllegalStateException("MythicMobs is not loaded");
        return instance;
    }

    /** Forgets everything derived from MythicMobs definitions (after a reload of MythicMobs or of this plugin). */
    public static void invalidateCaches() {
        CurrentMythicItemMatcher.invalidate();
        LegacyMythicItemMatcher.invalidate();
        VanillaMythicItems.invalidate();
    }

    public static final class Handler {
        private MythicBukkit mm() {
            return MythicBukkit.inst();
        }

        /** The MMID of the item, or null. */
        public String getID(ItemStack item) {
            if (item == null || item.getType().isAir()) return null;
            ItemStack copy = item.clone();
            copy.setAmount(1);
            return mm().getItemManager().getMythicTypeFromItem(copy);
        }

        public ItemStack getMythicItem(String id, int amount) {
            ItemStack item = mm().getItemManager().getItemStack(id);
            if (item == null) return null;
            item.setAmount(amount);
            return item;
        }

        public Collection<String> getMythicItemNames() {
            return new ArrayList<>(mm().getItemManager().getItemNames());
        }

        public boolean existsMythicMob(String id) {
            return mm().getAPIHelper().getMythicMob(id) != null;
        }

        public List<String> getMythicMobNames() {
            return new ArrayList<>(mm().getMobManager().getMobNames());
        }

        public Entity spawnMythicMob(Location location, String id) {
            try {
                return mm().getAPIHelper().spawnMythicMob(id, location);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to spawn MythicMob shop NPC " + id + " at " + location, e);
                return null;
            }
        }
    }
}
