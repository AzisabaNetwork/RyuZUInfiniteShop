package ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.NpcType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopFactory;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopRegistry;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LocationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LogUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ShopUtil {
    // ========== Registry ==========

    public static HashMap<String, Shop> getShops() {
        return ShopRegistry.getShops();
    }

    public static Shop getShop(String id) {
        return ShopRegistry.getShop(id);
    }

    public static void addShop(String id, Shop shop) {
        ShopRegistry.addShop(id, shop);
    }

    public static void removeShop(String id) {
        ShopRegistry.removeShop(id);
    }

    public static LinkedHashMap<String, Shop> getSortedShops(ShopMode mode, String name) {
        return ShopRegistry.getSortedShops(mode.equals(ShopMode.EDIT), name);
    }

    // ========== Factory ==========

    public static Shop createNewShop(Location location, String type, ConfigurationSection config) {
        return ShopFactory.createShop(location, type, config);
    }

    // ========== File I/O ==========

    public static boolean loadAllShops() {
        ShopLoadSession session = beginLoadAllShops();
        while (session.hasNext()) session.loadNext();
        return session.finish();
    }

    /** Starts loading shop files so callers can spread the work across ticks. */
    public static ShopLoadSession beginLoadAllShops() {
        getShops().clear();
        deferredShops.clear();
        File directory = FileUtil.initializeFolder("shops");
        return new ShopLoadSession(directory.listFiles());
    }

    public static final class ShopLoadSession {
        private final List<File> shopFiles;
        private final File legacySaveFile;
        private int index;

        private ShopLoadSession(File[] files) {
            List<File> found = new ArrayList<>();
            File legacy = null;
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File file : files) {
                    if (!file.getName().endsWith(".yml")) continue;
                    if (file.getName().equals("save.yml")) {
                        legacy = file;
                    } else {
                        found.add(file);
                    }
                }
            }
            this.shopFiles = found;
            this.legacySaveFile = legacy;
        }

        public boolean hasNext() {
            return index < shopFiles.size();
        }

        public void loadNext() {
            if (hasNext()) loadShopFile(shopFiles.get(index++));
        }

        public boolean finish() {
            logDeferredShops();
            return legacySaveFile != null && convertAllShopkeepers(legacySaveFile);
        }
    }

    /**
     * Loads one shop file. Files whose world is not loaded yet are deferred
     * until {@link #loadDeferredShops(String)} is called for that world, and a
     * broken file is logged and skipped so it cannot abort loading the rest.
     *
     * @return the loaded shop, or null when the file was deferred or failed
     */
    private static Shop loadShopFile(File file) {
        String id = file.getName().replace(".yml", "");
        String worldName = id.split(",", 2)[0];
        if (Bukkit.getWorld(worldName) == null) {
            deferShopFile(worldName, file, readTradeUUIDs(file));
            return null;
        }
        try {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
            Location location = LocationUtil.toLocationFromString(id);

            String type = config.getString("Npc.Options.EntityType", "VILLAGER");
            String mythicmob = config.getString("Npc.Options.MythicMob");
            String citizen = config.getString("Npc.Options.Citizen");
            if (mythicmob != null)
                return new Shop(location, mythicmob);
            else if (citizen != null)
                return new Shop(location, UUID.fromString(citizen), false);
            else
                return createNewShop(location, type, null);
        } catch (Exception e) {
            logSevere(LanguageKey.ERROR_FILE_LOADING.getMessage(file.getName()), e);
            return null;
        }
    }

    // ========== Shops in worlds that are not loaded ==========

    /** Shop files waiting for their world to load, keyed by lower-cased world name. */
    private static final Map<String, List<DeferredShop>> deferredShops = new java.util.concurrent.ConcurrentHashMap<>();

    private record DeferredShop(File file, Set<UUID> tradeUUIDs) {}

    private static String worldKey(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    private static void deferShopFile(String worldName, File file, Set<UUID> tradeUUIDs) {
        deferredShops.computeIfAbsent(worldKey(worldName), key -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(new DeferredShop(file, tradeUUIDs));
    }

    private static Set<UUID> readTradeUUIDs(File file) {
        Set<UUID> uuids = new HashSet<>();
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> trade : config.getMapList("Trades")) {
                Object uuid = trade.get("uuid");
                if (uuid == null) continue;
                try {
                    uuids.add(UUID.fromString(uuid.toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            logSevere("Failed to read trade IDs from deferred shop file " + file.getName(), e);
        }
        return uuids;
    }

    private static void logDeferredShops() {
        deferredShops.forEach((world, shops) -> logWarning("World '" + world + "' is not loaded; "
                + shops.size() + " shop(s) will be loaded when the world loads."));
    }

    /** Returns the number of shops waiting for a world to load. */
    public static int getDeferredShopCount() {
        return deferredShops.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Trade IDs referenced by shops whose world is not loaded. Their trade
     * options must survive saves even though the shops are not in the registry.
     */
    public static Set<UUID> getDeferredTradeUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        for (List<DeferredShop> shops : deferredShops.values())
            for (DeferredShop shop : shops) uuids.addAll(shop.tradeUUIDs());
        return uuids;
    }

    /** Loads the shops that were waiting for the given world. Must run on the main thread. */
    public static List<Shop> loadDeferredShops(String worldName) {
        List<DeferredShop> pending = deferredShops.get(worldKey(worldName));
        if (pending == null) return Collections.emptyList();
        List<Shop> loaded = new ArrayList<>();
        for (DeferredShop deferred : pending) {
            Shop shop = loadShopFile(deferred.file());
            if (shop != null) loaded.add(shop);
        }
        // Remove only after loading so a concurrent save never loses the trade IDs.
        deferredShops.remove(worldKey(worldName), pending);
        logInfo("Loaded " + loaded.size() + " shop(s) in world '" + worldName + "'.");
        return loaded;
    }

    /**
     * Takes the shops of a world that is about to unload out of the registry,
     * keeping them deferred so they come back when the world loads again.
     */
    public static void unloadShops(String worldName) {
        List<Shop> unloading = getShops().values().stream()
                .filter(shop -> shop.getLocation().isWorldLoaded()
                        && shop.getLocation().getWorld().getName().equalsIgnoreCase(worldName))
                .collect(Collectors.toList());
        for (Shop shop : unloading) {
            File file = FileUtil.initializeFile("shops/" + shop.getID() + ".yml");
            Set<UUID> tradeUUIDs = shop.getTrades().stream()
                    .map(ShopTrade.tradeUUID::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (shop.isDirty()) shop.saveYaml();
            deferShopFile(worldName, file, tradeUUIDs);
            shop.removeEntities();
            removeShop(shop.getID());
        }
    }

    private static java.util.logging.Logger logger() {
        RyuZUInfiniteShop plugin = RyuZUInfiniteShop.getPlugin();
        java.util.logging.Logger logger = plugin == null ? null : plugin.getLogger();
        return logger != null ? logger : Bukkit.getLogger();
    }

    private static void logInfo(String message) {
        logger().info(message);
    }

    private static void logWarning(String message) {
        logger().warning(message);
    }

    private static void logSevere(String message, Exception e) {
        logger().log(java.util.logging.Level.SEVERE, message, e);
    }

    public static void saveAllShops() {
        for (Shop shop : getShops().values()) {
            try {
                shop.saveYaml();
            } catch (Exception e) {
                throw new RuntimeException(LanguageKey.ERROR_FILE_SAVING.getMessage(shop.getID()), e);
            }
        }
    }

    public static void saveDirtyShops() {
        for (Shop shop : getShops().values()) {
            if (!shop.isDirty()) continue;
            try {
                shop.saveYaml();
            } catch (Exception e) {
                throw new RuntimeException(LanguageKey.ERROR_FILE_SAVING.getMessage(shop.getID()), e);
            }
        }
    }

    /**
     * Restores MythicMob-backed shop NPCs after an asynchronous save without
     * removing existing entities or reloading the shop YAML files.
     */
    public static void respawnMissingMythicShopNPCs() {
        Iterator<Shop> shops = new ArrayList<>(getShops().values()).iterator();
        Bukkit.getScheduler().runTaskTimer(RyuZUInfiniteShop.getPlugin(), (BukkitTask task) -> {
            for (int processed = 0; processed < 4 && shops.hasNext(); processed++) {
                Shop shop = shops.next();
                if (shop.getNpcType().equals(NpcType.MYTHICMOB)) {
                    shop.respawnNPC();
                }
            }
            if (!shops.hasNext()) {
                task.cancel();
            }
        }, 1L, 1L);
    }

    public static Shop reloadShop(Shop shop) {
        return reloadShop(shop.getLocation(), shop.convertShopToString(), TradeUtil.convertTradesToList(shop.convertTradesToMap()));
    }

    public static Shop reloadShop(Location location, String data, List<ShopTrade> trades) {
        File file = FileUtil.initializeFile("shops/" + LocationUtil.toStringFromLocation(location) + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(data);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }

        String stringLocation = LocationUtil.toStringFromLocation(location);
        if (getShops().containsKey(stringLocation)) getShop(stringLocation).removeShop();
        config.set("Trades", trades.stream().map(ShopTrade::serialize).collect(Collectors.toList()));

        try {
            config.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }

        String type = config.getString("Npc.Options.EntityType", "VILLAGER");
        String mythicmob = config.getString("Npc.Options.MythicMob");
        String citizen = config.getString("Npc.Options.Citizen");
        if (mythicmob != null)
            return new Shop(location, mythicmob);
        else if (citizen != null)
            return new Shop(location, UUID.fromString(citizen), false);
        else
            return createNewShop(location, type, null);
    }

    // ========== NPC ==========

    public static void removeAllNPC() {
        for (Shop shop : getShops().values())
            shop.removeNPC();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                String id = NBTUtil.getNMSStringTag(entity, "Shop");
                if (id != null)
                    entity.remove();
            }
        }
    }

    // ========== Inventory ==========

    public static ShopHolder getShopHolder(InventoryClickEvent event) {
        if (event.getAction().equals(InventoryAction.CLONE_STACK)) return null;
        return getShopHolder(getSecureInventory(event));
    }

    public static ShopHolder getShopHolder(Inventory inv) {
        ModeHolder holder = getModeHolder(inv);
        if (holder == null) return null;
        if (!(holder instanceof ShopHolder)) return null;
        return (ShopHolder) holder;
    }

    public static ModeHolder getModeHolder(InventoryClickEvent event) {
        if (event.getAction().equals(InventoryAction.CLONE_STACK)) return null;
        return getModeHolder(getSecureInventory(event));
    }

    public static ModeHolder getModeHolder(Inventory inv) {
        if (inv == null) return null;
        if (inv instanceof PlayerInventory) return null;
        InventoryHolder holder = inv.getHolder();
        if (holder == null) return null;
        if (!(holder instanceof ModeHolder)) return null;
        return (ModeHolder) holder;
    }

    public static Inventory getSecureInventory(InventoryClickEvent event) {
        return JavaUtil.getOrDefault(event.getClickedInventory(), event.getView().getTopInventory());
    }

    // ========== Merge ==========

    public static HashMap<String, String> mergeShop(ItemStack item, Shop shop, Player p) {
        YamlConfiguration config = new YamlConfiguration();
        String data = NBTUtil.getNMSStringTag(item, "ShopData");
        try {
            config.loadFromString(data);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        List<ShopTrade> trades = TradeUtil.convertTradesToList(item);
        trades.addAll(shop.getTrades());
        LogUtil.log(LogUtil.LogType.MERGESHOP, p.getName(), shop.getID());
        shop.removeShop();
        HashMap<String, String> shopData = new HashMap<>();
        shopData.put("ShopData", NBTUtil.getNMSStringTag(item, "ShopData"));
        shopData.putAll(TradeUtil.convertTradesToMap(item, trades.stream().distinct().collect(Collectors.toList())));
        return shopData;
    }

    // ========== Inventory Management ==========

    public static void closeShopTradeInventory(Player p, Shop shop) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
            ShopHolder holder = (ShopHolder) p.getOpenInventory().getTopInventory().getHolder();
            if (holder.getMode().equals(ShopMode.TRADE) && holder.getShop().equals(shop))
                p.closeInventory();
        }
    }

    public static void closeAllShopTradeInventory(Shop shop) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            closeShopTradeInventory(p, shop);
        }
    }

    public static ShopHolder closeShopTradeInventory(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
            ShopHolder holder = (ShopHolder) p.getOpenInventory().getTopInventory().getHolder();
            if (holder.getMode().equals(ShopMode.TRADE)) {
                p.closeInventory();
                return holder;
            }
        }
        return null;
    }

    public static ShopHolder closeShopInventory(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
            ShopHolder holder = (ShopHolder) p.getOpenInventory().getTopInventory().getHolder();
            holder.setBefore(null);
            p.closeInventory();
            return holder;
        }
        return null;
    }

    public static void forceClose(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ModeHolder) {
            ShopHolder holder = (ShopHolder) p.getOpenInventory().getTopInventory().getHolder();
            holder.setBefore(null);
        }
        p.closeInventory();
    }

    public static HashMap<Player, ShopHolder> getAllShopInventoryViewer() {
        HashMap<Player, ShopHolder> holders = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShopHolder holder = closeShopInventory(p);
            if (holder != null) holders.put(p, holder);
        }
        return holders;
    }

    public static void openAllShopInventory(HashMap<Player, ShopHolder> holders) {
        for (Player p : holders.keySet()) {
            ShopHolder holder = holders.get(p);
            p.openInventory(holder.getInventory());
        }
    }

    public static void reloadAllShopTradeInventory(Runnable runnable) {
        HashMap<Player, ShopHolder> holders = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShopHolder holder = closeShopTradeInventory(p);
            if (holder != null) holders.put(p, holder);
        }
        runnable.run();
        for (Player p : holders.keySet()) {
            ShopHolder holder = holders.get(p);
            p.openInventory(holder.getInventory());
        }
    }

    public static void reloadAllShopForMythicMobsReload(Runnable runnable) {
        HashMap<Player, ShopHolder> holders = getAllShopInventoryViewer();
        runnable.run();
        for (Shop shop : getShops().values()) {
            if (shop.getNpcType() == NpcType.MYTHICMOB) {
                shop.respawnNPC();
            }
        }
        openAllShopInventory(holders);
    }

    // ========== Shopkeepers Conversion ==========

    private static boolean convertAllShopkeepers(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        Set<String> keys = new HashSet<>();
        for (String key : config.getKeys(false)) {
            String base = key + ".";
            if (key.equals("data-version")) continue;
            try {
                EntityType type;
                try {
                    type = EntityType.valueOf(
                            config.getString(base + "object.type",
                                             config.getString(base + "object", "VILLAGER")
                            ).replace("-", "_").toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (!config.getString(base + "type", "none").equals("admin")) continue;
                if (Bukkit.getWorld(config.getString(base + ".world")) == null) continue;
                Location location = LocationUtil.toLocationFromString(config.getString(base + ".world") + "," + config.getString(base + "x") + "," + config.getString(base + "y") + "," + config.getString(base + "z"));
                if (getShops().containsKey(LocationUtil.toStringFromLocation(location))) {
                    Shop shop = getShop(LocationUtil.toStringFromLocation(location));
                    List<ShopTrade> trades = new ArrayList<>();
                    for (String recipe : config.getConfigurationSection(base + "recipes").getKeys(false)) {
                        boolean hasItem2 = config.contains(base + "recipes." + recipe + ".item2");
                        ItemStack[] items = new ItemStack[hasItem2 ? 2 : 1];
                        ItemStack[] results = new ItemStack[1];
                        results[0] = config.getItemStack(base + "recipes." + recipe + ".resultItem");
                        items[0] = config.getItemStack(base + "recipes." + recipe + ".item1");
                        if (hasItem2) items[1] = config.getItemStack(base + "recipes." + recipe + ".item2");
                        trades.add(new ShopTrade(results, items));
                    }
                    if (Config.overwriteConverting) {
                        shop.setNpcType(type.name());
                        shop.setDisplayName(config.getConfigurationSection(key).getString("name", "").isEmpty() ? "" : ChatColor.GREEN + config.getConfigurationSection(key).getString("name"));
                        shop.setNpcMetaFromShopkeepersConfiguration(config.getConfigurationSection(base + "object"));
                        shop.setTrades(trades);
                        reloadShop(shop);
                    } else
                        shop.addAllTrades(trades);
                    keys.add(key);
                } else {
                    Shop shop = createNewShop(location, type.name(), config.getConfigurationSection(key));
                    List<ShopTrade> trades = new ArrayList<>();
                    for (String recipe : config.getConfigurationSection(base + "recipes").getKeys(false)) {
                        boolean hasItem2 = config.contains(base + "recipes." + recipe + ".item2");
                        ItemStack[] items = new ItemStack[hasItem2 ? 2 : 1];
                        ItemStack[] results = new ItemStack[1];
                        results[0] = config.getItemStack(base + "recipes." + recipe + ".resultItem");
                        items[0] = config.getItemStack(base + "recipes." + recipe + ".item1");
                        if (hasItem2) items[1] = config.getItemStack(base + "recipes." + recipe + ".item2");
                        trades.add(new ShopTrade(results, items));
                    }
                    shop.setTrades(trades);
                    shop.setSearchable(Config.defaultSearchableInConverting);
                    shop.saveYaml();
                    keys.add(key);
                }
            } catch (Exception e) {
                throw new RuntimeException(LanguageKey.ERROR_FILE_CONVERTING.getMessage(key, config.getString(base + ".world"), config.getString(base + "x"), config.getString(base + "y"), config.getString(base + "z")), e);
            }
        }

        keys.forEach(key -> config.set(key, null));

        try {
            config.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }
        return !keys.isEmpty();
    }
}
