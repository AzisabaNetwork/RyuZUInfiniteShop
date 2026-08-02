package ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.TradeOption;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TradeUtil {
    public static boolean isAvailableTrade(Inventory inv, int slot, ShopType type) {
        return switch (type) {
            case TwotoOne ->
                    ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()).length != 0 && inv.getItem(slot + 3) != null;
            case FourtoFour ->
                    ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()).length != 0 && ItemUtil.getItemSet(inv, slot + 5, 4).length != 0;
            case SixtoTwo ->
                    ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()).length != 0 && ItemUtil.getItemSet(inv, slot + 7, 2).length != 0;
        };
    }

    public static ShopTrade getTrade(Inventory inv, int slot, ShopType type) {
        if (!isAvailableTrade(inv, slot, type)) return null;
        return switch (type) {
            case TwotoOne ->
                    new ShopTrade(new ItemStack[]{inv.getItem(slot + 3)}, ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()));
            case FourtoFour ->
                    new ShopTrade(ItemUtil.getItemSet(inv, slot + 5, 4), ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()));
            case SixtoTwo ->
                    new ShopTrade(ItemUtil.getItemSet(inv, slot + 7, 2), ItemUtil.getItemSet(inv, slot, type.getSubtractSlot()));
        };
    }

    public static void removeGarbageTradeOption() {
        Set<ShopTrade> trades = new HashSet<>();
        for (Shop shop : ShopUtil.getShops().values()) {
            trades.addAll(shop.getTrades());
        }
        ShopTrade.tradeOptions.keySet().removeIf(tradeID -> !trades.contains(ShopTrade.tradeUUID.inverse().get(tradeID)));
    }

    public static void saveTradeOptions() {
        removeGarbageTradeOption();
        File file = FileUtil.initializeFile("options.yml");
        YamlConfiguration config = new YamlConfiguration();
        ShopTrade.tradeUUID.values().forEach(tradeID -> ShopTrade.tradeUUID.inverse().get(tradeID).saveTradeOption(config));
        try {
            config.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }
    }

    public static void loadTradeOptions() {
        File file = FileUtil.initializeFile("options.yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String tradeID : config.getKeys(false)) {
            ShopTrade.tradeOptions.put(UUID.fromString(tradeID), config.getSerializable(tradeID + ".options", TradeOption.class, new TradeOption()));
            if (config.contains(tradeID + ".counts"))
                for (String playerID : config.getConfigurationSection(tradeID + ".counts").getKeys(false))
                    ShopTrade.tradeCounts.put(UUID.fromString(playerID), UUID.fromString(tradeID), config.getInt(tradeID + ".counts." + playerID));
        }
    }

    public static ShopTrade getFirstTrade(ItemStack item) {
        String tag = NBTUtil.getNMSStringTag(item, "TradesSize");
        if (tag == null) return null;
        ShopType shoptype = ShopType.valueOf(NBTUtil.getNMSStringTag(item, "ShopType"));
        return new ShopTrade(ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Give" + 0)), ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Take" + 0)));
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromGive(ItemStack item, ShopMode mode) {
        LinkedHashMap<ShopTrade, Shop> trades = new LinkedHashMap<>();
        ShopUtil.getShops().values().forEach(shop -> {
            if (shop.isSearchable() || mode.equals(ShopMode.EDIT))
                shop.getTrades().forEach(trade -> Arrays.stream(trade.getGiveItems()).forEach(give -> {
                                             if (give.isSimilar(item))
                                                 trades.put(trade, shop);
                                         })
                );
        });
        return trades;
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromTake(ItemStack item, ShopMode mode) {
        LinkedHashMap<ShopTrade, Shop> trades = new LinkedHashMap<>();
        ShopUtil.getShops().values().forEach(shop -> {
            if (shop.isSearchable() || mode.equals(ShopMode.EDIT))
                shop.getTrades().forEach(trade -> Arrays.stream(trade.getTakeItems()).forEach(take -> {
                                             if (take.isSimilar(item))
                                                 trades.put(trade, shop);
                                         })
                );
        });
        return trades;
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromGiveByDisplayName(String name, ShopMode mode) {
        LinkedHashMap<ShopTrade, Shop> trades = new LinkedHashMap<>();
        ShopUtil.getShops().values().forEach(shop -> {
            if (shop.isSearchable() || mode.equals(ShopMode.EDIT))
                shop.getTrades().forEach(trade -> Arrays.stream(trade.getGiveItems()).forEach(take -> {
                                             if (JavaUtil.containsIgnoreCase(take.getItemMeta().getDisplayName(), name))
                                                 trades.put(trade, shop);
                                         })
                );
        });
        return trades;
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromTakeByDisplayName(String name, ShopMode mode) {
        LinkedHashMap<ShopTrade, Shop> trades = new LinkedHashMap<>();
        ShopUtil.getShops().values().forEach(shop -> {
            if (shop.isSearchable() || mode.equals(ShopMode.EDIT))
                shop.getTrades().forEach(trade -> Arrays.stream(trade.getTakeItems()).forEach(take -> {
                                             if (JavaUtil.containsIgnoreCase(take.getItemMeta().getDisplayName(), name))
                                                 trades.put(trade, shop);
                                         })
                );
        });
        return trades;
    }

    public static List<ShopTrade> convertTradesToList(HashMap<String, String> trades) {
        String tag = trades.get("TradesSize");
        if (tag == null) return null;
        List<ShopTrade> temp = new ArrayList<>();
        for (int i = 0; i < Integer.parseInt(tag); i++) {
            temp.add(new ShopTrade(ItemUtil.toItemStackArrayFromString(trades.get("Give" + i)), ItemUtil.toItemStackArrayFromString(trades.get("Take" + i))));
        }
        return temp;
    }

    public static List<ShopTrade> convertTradesToList(ItemStack item) {
        String tag = NBTUtil.getNMSStringTag(item, "TradesSize");
        if (tag == null) return null;
        List<ShopTrade> temp = new ArrayList<>();
        for (int i = 0; i < Integer.parseInt(tag); i++) {
            temp.add(new ShopTrade(ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Give" + i)), ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Take" + i))));
        }
        return temp;
    }

    public static Map<String, String> convertTradesToMap(ItemStack item) {
        String tag = NBTUtil.getNMSStringTag(item, "TradesSize");
        if (tag == null) return null;
        HashMap<String, String> temp = new HashMap<>();
        temp.put("TradesSize", tag);
        temp.put("ShopType", NBTUtil.getNMSStringTag(item, "ShopType"));
        for (int i = 0; i < Integer.parseInt(tag); i++) {
            temp.put("Give" + i, NBTUtil.getNMSStringTag(item, "Give" + i));
            temp.put("Take" + i, NBTUtil.getNMSStringTag(item, "Take" + i));
        }
        return temp;
    }

    public static Map<String, String> convertTradesToMap(ItemStack item, List<ShopTrade> trades) {
        String tag = NBTUtil.getNMSStringTag(item, "TradesSize");
        if (tag == null) return null;
        HashMap<String, String> temp = new HashMap<>();
        temp.put("ShopType", NBTUtil.getNMSStringTag(item, "ShopType"));
        temp.put("TradesSize", String.valueOf(trades.size()));
        for (int i = 0; i < trades.size(); i++) {
            temp.put("Give" + i, ItemUtil.toStringFromItemStackArray(trades.get(i).getGiveItems()));
            temp.put("Take" + i, ItemUtil.toStringFromItemStackArray(trades.get(i).getTakeItems()));
        }
        return temp;
    }
}
