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
    public static boolean saveByMMID;
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
        saveByMMID = yaml.getBoolean("SaveByMMID", true);
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
        if (!yaml.contains("SaveByMMID")) yaml.set("SaveByMMID", saveByMMID);
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

    public static void runAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (respawnTask != null) respawnTask.cancel();
        if (autoSaveInterval > 0) autoSaveTask = Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), () -> FileUtil.saveDirty(), 20L * 60 * autoSaveInterval, 20L * 60 * autoSaveInterval);
        respawnTask = Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), () -> ShopUtil.getShops().values().forEach(Shop::respawnNPC), 20L, 20L * 10);
    }
}
