package ryuzuinfiniteshop.ryuzuinfiniteshop.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.io.File;
import java.io.IOException;

public class Config {
    public static int autoSaveInterval;
    public static boolean editLog;
    public static boolean tradeLog;
    public static boolean backupDiff;
    /**
     * @deprecated MythicMobs items are always saved by their MMID now; the setting is only kept so old configs load.
     */
    @Deprecated
    public static boolean saveByMMID;
    /** MMIDs whose items are kept as they are instead of being saved by MMID (case-insensitive, "Prefix*" allowed). */
    public static java.util.List<String> mythicItemExcludedIds = new java.util.ArrayList<>();
    public static boolean overwriteConverting;
    public static boolean defaultSearchableInConverting;
    public static boolean followPlayer;
    public static boolean readOnlyIgnoreIOException;
    public static String language;
    public static BukkitTask respawnTask;
    private static BukkitTask autoSaveTask;

    public static void load() {
        File file = FileUtil.initializeFile("config.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        autoSaveInterval = yaml.getInt("AutoSaveInterval", 0);
        editLog = yaml.getBoolean("EditLog", true);
        tradeLog = yaml.getBoolean("TradeLog", true);
        backupDiff = yaml.getBoolean("BackupDiff", true);
        saveByMMID = yaml.getBoolean("SaveByMMID", true);
        mythicItemExcludedIds = new java.util.ArrayList<>(yaml.getStringList("MythicItemExcludedIDs"));
        overwriteConverting = yaml.getBoolean("OverwriteConverting", false);
        defaultSearchableInConverting = yaml.getBoolean("DefaultSearchableInConverting", true);
        followPlayer = yaml.getBoolean("FollowPlayer", false);
        language = yaml.getString("Language", "english").toLowerCase();
        readOnlyIgnoreIOException = yaml.getBoolean("ReadOnlyIgnoreIOException", false);
    }

    public static void save() {
        File file = FileUtil.initializeFile("config.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        if (!yaml.contains("AutoSaveInterval")) yaml.set("AutoSaveInterval", autoSaveInterval);
        if (!yaml.contains("EditLog")) yaml.set("EditLog", editLog);
        if (!yaml.contains("TradeLog")) yaml.set("TradeLog", tradeLog);
        if (!yaml.contains("BackupDiff")) yaml.set("BackupDiff", backupDiff);
        if (!yaml.contains("SaveByMMID")) yaml.set("SaveByMMID", saveByMMID);
        if (!yaml.contains("MythicItemExcludedIDs")) yaml.set("MythicItemExcludedIDs", mythicItemExcludedIds);
        if (!yaml.contains("OverwriteConverting")) yaml.set("OverwriteConverting", overwriteConverting);
        if (!yaml.contains("DefaultSearchableInConverting"))
            yaml.set("DefaultSearchableInConverting", defaultSearchableInConverting);
        if (!yaml.contains("FollowPlayer")) yaml.set("FollowPlayer", followPlayer);
        if (!yaml.contains("Language")) yaml.set("Language", language);
        if (!yaml.contains("ReadOnlyIgnoreIOException"))
            yaml.set("ReadOnlyIgnoreIOException", readOnlyIgnoreIOException);

        try {
            yaml.save(file);
        } catch (IOException e) {
            if (!readOnlyIgnoreIOException) e.printStackTrace();
        }
    }

    /** Whether items of this MMID are excluded from being saved by MMID (see {@code MythicItemExcludedIDs}). */
    public static boolean isMythicItemExcluded(String id) {
        if (id == null || mythicItemExcludedIds == null) return false;
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        for (String entry : mythicItemExcludedIds) {
            if (entry == null || entry.isBlank()) continue;
            String pattern = entry.trim().toLowerCase(java.util.Locale.ROOT);
            if (pattern.endsWith("*") ? lower.startsWith(pattern.substring(0, pattern.length() - 1)) : lower.equals(pattern))
                return true;
        }
        return false;
    }

    public static void runAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (respawnTask != null) respawnTask.cancel();
        if (autoSaveInterval > 0) autoSaveTask = Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), () -> FileUtil.saveDirty(), 20L * 60 * autoSaveInterval, 20L * 60 * autoSaveInterval);
        respawnTask = Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), () -> {
            // One broken shop must not stop the remaining shops from respawning.
            for (Shop shop : new java.util.ArrayList<>(ShopUtil.getShops().values())) {
                try {
                    shop.respawnNPC();
                } catch (Exception e) {
                    RyuZUInfiniteShop.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                            "Failed to respawn shop NPC: " + shop.getLocation(), e);
                }
            }
        }, 20L, 20L * 10);
    }
}
