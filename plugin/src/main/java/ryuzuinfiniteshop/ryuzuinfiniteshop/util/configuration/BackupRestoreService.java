package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Lists and restores the diff history kept by {@link DiffBackupUtil}. Targets are a shop ID
 * ({@code world,x,y,z}) or {@link #OPTIONS} for options.yml. Must run on the main thread
 * while the caller holds {@link FileUtil#getSaveBlock()}.
 */
public final class BackupRestoreService {
    public static final String OPTIONS = "options";

    private BackupRestoreService() {}

    public static List<DiffBackupUtil.Entry> list(String target) throws IOException {
        return DiffBackupUtil.readEntries(DiffBackupUtil.historyFile(target));
    }

    /** Names that have a history file, for tab completion. */
    public static List<String> targets() {
        File[] files = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/backup")
                .listFiles((dir, name) -> name.endsWith(".diff"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files)
                .map(f -> f.getName().substring(0, f.getName().length() - ".diff".length()))
                .sorted()
                .toList();
    }

    /**
     * Rolls the target back to its state right after history entry {@code keep} (1-based; 0 = before
     * the first entry). The restore itself is appended to the history, so it can be undone too.
     *
     * @throws IllegalStateException when the history does not match the current file
     */
    public static void restore(String target, int keep) throws IOException {
        if (target.equals(OPTIONS)) restoreOptions(keep);
        else restoreShop(target, keep);
    }

    private static void restoreShop(String id, int keep) throws IOException {
        if (!LocationUtil.isLocationString(id) || Bukkit.getWorld(id.split(",", 2)[0]) == null)
            throw new IllegalStateException("World of shop " + id + " is not loaded");
        Shop shop = ShopUtil.getShops().get(id);
        // Flush unsaved edits so the history ends at the state that is being rolled back.
        if (shop != null) shop.saveYaml();

        File file = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/" + id + ".yml");
        String restored = DiffBackupUtil.restore(read(file), list(id), keep);
        if (restored.isEmpty()) {
            // The shop did not exist at that point.
            if (shop != null) shop.removeShop();
            return;
        }
        Location location = LocationUtil.toLocationFromString(id);
        ShopUtil.replaceShopData(location, restored).respawnNPC();
    }

    private static void restoreOptions(int keep) throws IOException {
        TradeUtil.saveTradeOptions();
        File file = FileUtil.initializeFile("options.yml");
        String restored = DiffBackupUtil.restore(read(file), list(OPTIONS), keep);
        DiffBackupUtil.write(file, restored, DiffBackupUtil.backupFile(OPTIONS), "options.yml");
        ShopTrade.tradeOptions.clear();
        ShopTrade.tradeCounts.clear();
        TradeUtil.loadTradeOptions();
    }

    private static String read(File file) throws IOException {
        return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : "";
    }
}
