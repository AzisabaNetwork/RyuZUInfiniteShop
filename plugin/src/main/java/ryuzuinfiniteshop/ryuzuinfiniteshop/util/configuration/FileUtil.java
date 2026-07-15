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

public class FileUtil {
    @Getter
    private static boolean saveBlock = false;

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
        if (saveBlock) return false;

        ShopUtil.removeAllNPC();
        saveBlock = true;
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
        saveBlock = false;
        if (converted) saveAll();
        Config.runAutoSave();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_RELOADING_COMPLETE.getMessage()));
        ShopUtil.getShops().values().forEach(Shop::respawnNPC);
        ShopUtil.openAllShopInventory(viewer);
        return true;
    }

    public static boolean loadAll() {
        if (saveBlock) return false;

        ShopUtil.removeAllNPC();
        saveBlock = true;
        Config.load();
        LanguageConfig.load();
        DisplayPanelConfig.load();
        UnderstandSystemConfig.load();
        boolean converted = ShopUtil.loadAllShops();
        TradeUtil.loadTradeOptions();
        saveBlock = false;
        if (converted) saveAll(false);
        Config.runAutoSave();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_LOADING_COMPLETE.getMessage()));
        ShopUtil.getShops().values().forEach(Shop::respawnNPC);
        return true;
    }

    public static boolean saveAll() {
        return saveAll(true);
    }

    public static boolean saveAll(boolean message) {
        if (saveBlock) return false;

        saveBlock = true;
        Bukkit.getScheduler().runTaskAsynchronously(RyuZUInfiniteShop.getPlugin(), () -> {
            TradeUtil.saveTradeOptions();
            ShopUtil.saveAllShops();
            UnderstandSystemConfig.save();
            Config.save();
            LanguageConfig.save();
            DisplayPanelConfig.save();
            Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> {
                saveBlock = false;
                if(message) Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_SAVING_COMPLETE.getMessage()));
            });
        });
        return true;
    }

    public static void saveAllSync() {
        ShopUtil.removeAllNPC();
        ShopUtil.getAllShopInventoryViewer();
        TradeUtil.saveTradeOptions();
        ShopUtil.saveAllShops();
        UnderstandSystemConfig.save();
        DisplayPanelConfig.save();
        Config.save();
        LanguageConfig.save();
    }

    public static boolean isSaveBlock(Player p) {
        if (saveBlock) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_FILES_RELOADING_BLOCKED.getMessage());
            SoundUtil.playFailSound(p);
        }
        return saveBlock;
    }

    public static boolean isSaveBlock(CommandData data) {
        if (saveBlock)
            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_FILES_RELOADING_BLOCKED.getMessage());

        return saveBlock;
    }
}
