package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.ryuzucommandsgenerator.CommandData;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileUtil {
    @Getter
    private static final AtomicBoolean saveBlock = new AtomicBoolean(false);
    private static final Object saveLock = new Object();
    private static final long RELOAD_TICK_BUDGET_NANOS = 8_000_000L;
    private static final ThreadLocal<Boolean> internalReloadOperation = ThreadLocal.withInitial(() -> false);
    /**
     * YAML serialization and disk writes are blocking work. A dedicated Java 21
     * virtual thread keeps them out of both the server thread and Bukkit's shared
     * asynchronous scheduler.
     */
    private static final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("SearchableInfiniteShop-Save-", 0).factory());

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
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> reloadAllWithMessage());
            return true;
        }
        if (!saveBlock.compareAndSet(false, true)) return false;
        try {
            ShopUtil.removeAllNPC();
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_RELOADING_FILES.getMessage()));
            HashMap<Player, ShopHolder> viewer = ShopUtil.getAllShopInventoryViewer();
            Config.load();
            LanguageConfig.load();
            new ReloadSession(viewer, new ArrayList<>(ShopUtil.getShops().values())).start();
            return true;
        } catch (Exception e) {
            RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to reload shop data: " + e.getMessage());
            e.printStackTrace();
            saveBlock.set(false);
            return false;
        }
    }

    /** Spreads blocking reload work over ticks while keeping Bukkit access on the main thread. */
    private static final class ReloadSession implements Runnable {
        private enum Phase {
            SAVE_OPTIONS, SAVE_SHOPS, SAVE_CONFIGS, PREPARE_LOAD,
            LOAD_SHOPS, LOAD_OPTIONS, RESPAWN, COMPLETE
        }

        private final HashMap<Player, ShopHolder> viewers;
        private final Iterator<Shop> shopsToSave;
        private Iterator<Shop> shopsToRespawn;
        private ShopUtil.ShopLoadSession shopLoader;
        private BukkitTask task;
        private Phase phase = Phase.SAVE_OPTIONS;
        private boolean converted;
        private final long startedAt = System.nanoTime();

        private ReloadSession(HashMap<Player, ShopHolder> viewers, List<Shop> shops) {
            this.viewers = viewers;
            this.shopsToSave = shops.iterator();
        }

        private void start() {
            task = Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), this, 1L, 1L);
        }

        @Override
        public void run() {
            long deadline = System.nanoTime() + RELOAD_TICK_BUDGET_NANOS;
            try {
                do {
                    Phase stepPhase = phase;
                    long stepStartedAt = System.nanoTime();
                    processOneStep();
                    long stepMillis = (System.nanoTime() - stepStartedAt) / 1_000_000L;
                    if (stepMillis >= 50L) {
                        RyuZUInfiniteShop.getPlugin().getLogger().warning(
                                "SIS reload step " + stepPhase + " took " + stepMillis + " ms");
                    }
                } while (phase != Phase.COMPLETE && System.nanoTime() < deadline);

                if (phase == Phase.COMPLETE) complete();
            } catch (Exception e) {
                fail(e);
            }
        }

        private void processOneStep() {
            switch (phase) {
                case SAVE_OPTIONS -> {
                    TradeUtil.saveTradeOptions();
                    phase = Phase.SAVE_SHOPS;
                }
                case SAVE_SHOPS -> {
                    if (shopsToSave.hasNext()) shopsToSave.next().saveYaml();
                    else phase = Phase.SAVE_CONFIGS;
                }
                case SAVE_CONFIGS -> {
                    UnderstandSystemConfig.save();
                    Config.save();
                    LanguageConfig.save();
                    DisplayPanelConfig.save();
                    DisplayPanelConfig.load();
                    phase = Phase.PREPARE_LOAD;
                }
                case PREPARE_LOAD -> {
                    shopLoader = ShopUtil.beginLoadAllShops();
                    phase = Phase.LOAD_SHOPS;
                }
                case LOAD_SHOPS -> {
                    if (shopLoader.hasNext()) shopLoader.loadNext();
                    else {
                        converted = shopLoader.finish();
                        phase = Phase.LOAD_OPTIONS;
                    }
                }
                case LOAD_OPTIONS -> {
                    TradeUtil.loadTradeOptions();
                    Config.runAutoSave();
                    shopsToRespawn = new ArrayList<>(ShopUtil.getShops().values()).iterator();
                    phase = Phase.RESPAWN;
                }
                case RESPAWN -> {
                    if (shopsToRespawn.hasNext()) {
                        runInternalReloadOperation(shopsToRespawn.next()::respawnNPC);
                    } else {
                        phase = Phase.COMPLETE;
                    }
                }
                case COMPLETE -> { }
            }
        }

        private void complete() {
            task.cancel();
            saveBlock.set(false);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_RELOADING_COMPLETE.getMessage()));
            ShopUtil.openAllShopInventory(viewers);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            RyuZUInfiniteShop.getPlugin().getLogger().info(
                    "SIS reload completed in " + elapsedMillis + " ms across multiple server ticks");
            if (converted) Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> saveAll());
        }

        private void fail(Exception e) {
            if (task != null) task.cancel();
            saveBlock.set(false);
            RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to reload shop data: " + e.getMessage());
            e.printStackTrace();
            ShopUtil.openAllShopInventory(viewers);
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
            Config.runAutoSave();
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_FILES_LOADING_COMPLETE.getMessage()));
            ShopUtil.getShops().values().forEach(Shop::respawnNPC);
            if (converted) Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), () -> saveAll(false));
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

    /** Flushes only data changed since the prior save; used by periodic autosave. */
    public static boolean saveDirty() {
        if (!saveBlock.compareAndSet(false, true)) return false;
        saveExecutor.execute(() -> {
            try {
                saveDirtyData();
            } catch (Exception e) {
                RyuZUInfiniteShop.getPlugin().getLogger().severe("Failed to save changed shop data: " + e.getMessage());
                e.printStackTrace();
            } finally {
                saveBlock.set(false);
            }
        });
        return true;
    }

    public static boolean saveAll(boolean message) {
        if (!saveBlock.compareAndSet(false, true)) return false;
        saveExecutor.execute(() -> {
            boolean saved = false;
            try {
                saveAllData();
                saved = true;
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
                if (saved) {
                    Bukkit.getScheduler().runTask(RyuZUInfiniteShop.getPlugin(), ShopUtil::respawnMissingMythicShopNPCs);
                }
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

    /** Stops the background saver during plugin shutdown after pending work completes. */
    public static void shutdownSaver() {
        saveExecutor.shutdown();
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

    private static void saveDirtyData() {
        synchronized (saveLock) {
            TradeUtil.saveDirtyTradeOptions();
            ShopUtil.saveDirtyShops();
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
        return saveBlock.get() && !internalReloadOperation.get();
    }

    private static void runInternalReloadOperation(Runnable operation) {
        internalReloadOperation.set(true);
        try {
            operation.run();
        } finally {
            internalReloadOperation.remove();
        }
    }
}
