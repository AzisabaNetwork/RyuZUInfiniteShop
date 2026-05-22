package ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Colorable;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ModeHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ShopUtil {
    @Getter
    private static HashMap<String, Shop> shops = new HashMap<>();

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

    public static boolean loadAllShops() {
        getShops().clear();
        File directory = FileUtil.initializeFolder("shops");
        File[] ItemFiles = directory.listFiles();
        if (ItemFiles == null) return false;
        File saveYaml = null;
        for (File f : ItemFiles) {
            try {
                if (!f.getName().endsWith(".yml")) continue;
                if (f.getName().equals("save.yml")) {
                    saveYaml = f;
                    continue;
                }
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(f);
                } catch (IOException | InvalidConfigurationException e) {
                    e.printStackTrace();
                }
                Location location = LocationUtil.toLocationFromString(f.getName().replace(".yml", ""));

                String type = config.getString("Npc.Options.EntityType", "VILLAGER");
                String mythicmob = config.getString("Npc.Options.MythicMob");
                String citizen = config.getString("Npc.Options.Citizen");
                if (mythicmob != null)
                    new Shop(location, mythicmob);
                if (citizen != null)
                    new Shop(location, UUID.fromString(citizen), false);
                else
                    createNewShop(location, type, null);
            } catch (Exception e) {
                throw new RuntimeException(LanguageKey.ERROR_FILE_LOADING.getMessage(f.getName()), e);
            }
        }
        return saveYaml != null && convertAllShopkeepers(saveYaml);
    }

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
                if (shops.containsKey(LocationUtil.toStringFromLocation(location))) {
                    //コンバート先の座標にすでにSISのSHOPがおかれている場合の処理
                    Shop shop = shops.get(LocationUtil.toStringFromLocation(location));
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
                        shop.setNpcMetaFromShopkeepersConfiguration(config.getConfigurationSection(RyuZUInfiniteShop.VERSION < 14 ? key : base + "object"));
                        shop.setTrades(trades);
                        reloadShop(shop);
                    } else
                        shop.addAllTrades(trades);
                    keys.add(key);
                } else {
                    //コンバート先の座標に新期でSISのSHOPが置く場合の処理
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

    public static void removeAllNPC() {
        for (Shop shop : ShopUtil.getShops().values())
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

    public static void saveAllShops() {
        for (Shop shop : getShops().values()) {
            try {
                shop.saveYaml();
            } catch (Exception e) {
                throw new RuntimeException(LanguageKey.ERROR_FILE_SAVING.getMessage(shop.getID()), e);
            }
        }
    }

    public static LinkedHashMap<String, Shop> getSortedShops(ShopMode mode, String name) {
        LinkedHashMap<String, Shop> sorted = new LinkedHashMap<>();
        if (mode.equals(ShopMode.EDIT))
            shops.keySet().stream().sorted(Comparator.naturalOrder()).filter(key -> shops.get(key).containsDisplayName(name) || name == null).forEach(key -> sorted.put(key, shops.get(key)));
        else
            shops.keySet().stream().sorted(Comparator.naturalOrder()).filter(key -> shops.get(key).isSearchable() && (shops.get(key).containsDisplayName(name) || name == null)).forEach(key -> sorted.put(key, shops.get(key)));

        return sorted;
    }

    public static Shop getShop(String id) {
        return shops.get(id);
    }

    public static void addShop(String id, Shop shop) {
        shops.put(id, shop);
    }

    public static Shop createNewShop(Location location, String type, ConfigurationSection config) {
        if (type.equalsIgnoreCase("BLOCK"))
            return new Shop(location, type, config);
        EntityType entityType = EntityType.valueOf(type);
        if (entityType.equals(EntityType.VILLAGER) || entityType.equals(EntityType.ZOMBIE_VILLAGER))
            return new VillagerableShop(location, type, config);
        if (entityType.equals(EntityType.CREEPER))
            return new PoweredableShop(location, type, config);
        if (Slime.class.isAssignableFrom(entityType.getEntityClass()))
            return new SlimeShop(location, type, config);
        if (Colorable.class.isAssignableFrom(entityType.getEntityClass()) || entityType.equals(EntityType.WOLF))
            return new DyeableShop(location, type, config);
        if (entityType.equals(EntityType.PARROT))
            return new ParrotShop(location, type, config);
        if (RyuZUInfiniteShop.VERSION >= 13 && entityType.equals(EntityType.CAT))
            return new CatShop(location, type, config);
        if (RyuZUInfiniteShop.VERSION >= 18 && entityType.equals(EntityType.AXOLOTL))
            return new AxolotlShop(location, type, config);
        if (entityType.equals(EntityType.SNOW_GOLEM))
            return new SnowmanShop(location, type, config);
        if (entityType.equals(EntityType.RABBIT))
            return new RabbitShop(location, type, config);
        if (entityType.equals(EntityType.HORSE))
            return new HorseShop(location, type, config);
        if (Ageable.class.isAssignableFrom(entityType.getEntityClass()))
            return new AgeableShop(location, type, config);
        if (RyuZUInfiniteShop.VERSION >= 13 && entityType.equals(EntityType.TROPICAL_FISH))
            return new TropicalFishShop(location, type, config);
        return new Shop(location, type, config);
    }

    public static void removeShop(String id) {
        shops.remove(id);
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
        if (shops.containsKey(stringLocation)) shops.get(stringLocation).removeShop();
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
        if (citizen != null)
            return new Shop(location, UUID.fromString(citizen), false);
        else
            return createNewShop(location, type, null);
    }

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
}
