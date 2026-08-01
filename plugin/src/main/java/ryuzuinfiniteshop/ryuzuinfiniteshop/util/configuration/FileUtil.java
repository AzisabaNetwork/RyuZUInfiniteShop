package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.ryuzucommandsgenerator.CommandData;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileUtil {
    @Getter
    private static final AtomicBoolean saveBlock = new AtomicBoolean(false);
    private static final Object saveLock = new Object();

    public static File initializeFile(String path) {
        String[] splited = path.split("/");
        File folder = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), String.join("", Arrays.copyOf(splited, splited.length - 1)));
        File file = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), path);
        if (!folder.exists()) folder.mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
            }
        }
        return file;
    }

    public static File initializeFolder(String path) {
        File folder = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), path);
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    public static boolean reloadAllWithMessage() {
        if (!saveBlock.compareAndSet(false, true)) return false;
        try {
        ShopUtil.removeAllNPC();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_RELOADING_FILES.getMessage()));
        HashMap<Player, ShopHolder> viewer = ShopUtil.getAllShopInventoryViewer();
        Config.load();
        LanguageConfig.load();
        TradeUtil.saveTradeOptions();
        ShopUtil.saveAllShops();
        UnderstandSystemConfig.save();
        Config.save();
        LanguageConfig.save();
        DisplayPanelConfig.save();
        DisplayPanelConfig.load();
        boolean converted = ShopUtil.loadAllShops();
        TradeUtil.loadTradeOptions();
        if (converted) Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> saveAll());
        Config.runAutoSave();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_RELOADING_COMPLETE.getMessage()));
        ShopUtil.getShops().values().forEach(Shop::respawnNPC);
        ShopUtil.openAllShopInventory(viewer);
        return true;
        } catch (Exception e) {
            RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to reload shop data: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            saveBlock.set(false);
        }
    }

    public static boolean loadAll() {
        if (!saveBlock.compareAndSet(false, true)) return false;
        try {
        ShopUtil.removeAllNPC();
        Config.load();
        LanguageConfig.load();
        DisplayPanelConfig.load();
        UnderstandSystemConfig.load();
        boolean converted = ShopUtil.loadAllShops();
        TradeUtil.loadTradeOptions();
        if (converted) Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> saveAll(false));
        Config.runAutoSave();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_LOADING_COMPLETE.getMessage()));
        ShopUtil.getShops().values().forEach(Shop::respawnNPC);
        return true;
        } catch (Exception e) {
            RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to load shop data: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            saveBlock.set(false);
        }
    }

    public static boolean saveAll() {
        return saveAll(true);
    }

    public static boolean saveAll(boolean message) {
        if (!saveBlock.compareAndSet(false, true)) return false;
        Bukkit.getScheduler().runTaskAsynchronously(RyuZUInfiniteShop.getPlugin(), () -> {
            try {
                saveAllData();
                if (message) {
                    Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () ->
                            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_SAVING_COMPLETE.getMessage()))
                    );
                }
            } catch (Exception e) {
                RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to save shop data: " + e.getMessage());
                e.printStackTrace();
            } finally {
                saveBlock.set(false);
            }
        });
        return true;
    }

    public static void saveAllSync() {
        synchronized (saveLock) {
            ShopUtil.removeAllNPC();
            ShopUtil.getAllShopInventoryViewer();
            saveAllData();
        }
    }

    private static void saveAllData() {
        synchronized (saveLock) {
            TradeUtil.saveTradeOptions();
            ShopUtil.saveAllShops();
            UnderstandSystemConfig.save();
            Config.save();
            LanguageConfig.save();
            DisplayPanelConfig.save();
        }
    }

    public static boolean isSaveBlock(Player p) {
        if (saveBlock.get()) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_FILES_RELOADING_BLOCKED.getMessage());
            SoundUtil.playFailSound(p);
        }
        return saveBlock.get();
    }

    public static boolean isSaveBlock(CommandData data) {
        if (saveBlock.get())
            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_FILES_RELOADING_BLOCKED.getMessage());

        return saveBlock.get();
    }

    public static boolean isSaveBlock() {
        return saveBlock.get();
    }
}
