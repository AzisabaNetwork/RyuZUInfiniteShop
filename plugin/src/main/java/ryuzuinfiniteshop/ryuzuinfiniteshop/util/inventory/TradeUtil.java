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
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.ObjectItems;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.DiffBackupUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;

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
        if (ShopTrade.tradeOptions.isEmpty()) return;
        Set<UUID> activeUUIDs = new HashSet<>();
        for (Shop shop : ShopUtil.getShops().values()) {
            for (ShopTrade trade : shop.getTrades()) {
                UUID uuid = ShopTrade.tradeUUID.get(trade);
                if (uuid != null) activeUUIDs.add(uuid);
            }
        }
        // Shops in unloaded worlds are not in the registry, but their options are still in use.
        activeUUIDs.addAll(ShopUtil.getDeferredTradeUUIDs());
        ShopTrade.tradeOptions.keySet().removeIf(tradeID -> !activeUUIDs.contains(tradeID));
    }

    public static void saveTradeOptions() {
        File file = FileUtil.initializeFile("options.yml");
        YamlConfiguration config = new YamlConfiguration();
        if (ShopTrade.tradeOptions.isEmpty()) {
            try {
                DiffBackupUtil.write(file, config.saveToString(), DiffBackupUtil.backupFile("options"), "options.yml");
            } catch (IOException e) {
                if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
            }
            return;
        }

        removeGarbageTradeOption();
        ShopTrade.tradeUUID.forEach((trade, tradeID) -> trade.saveTradeOption(config, tradeID));
        for (UUID tradeID : ShopUtil.getDeferredTradeUUIDs()) {
            if (!config.contains(tradeID.toString())) ShopTrade.writeTradeOption(config, tradeID);
        }
        try {
            DiffBackupUtil.write(file, config.saveToString(), DiffBackupUtil.backupFile("options"), "options.yml");
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }
    }

    /** Writes options.yml only when trade options or limited-trade counts changed. */
    public static void saveDirtyTradeOptions() {
        if (!ShopTrade.consumeOptionsDirty()) return;
        saveTradeOptions();
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
        ShopTrade.resolveDuplicatedTradeUUIDs();
    }

    public static ShopTrade getFirstTrade(ItemStack item) {
        String tag = NBTUtil.getNMSStringTag(item, "TradesSize");
        if (tag == null) return null;
        ShopType shoptype = ShopType.valueOf(NBTUtil.getNMSStringTag(item, "ShopType"));
        return new ShopTrade(ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Give" + 0)), ItemUtil.toItemStackArrayFromString(NBTUtil.getNMSStringTag(item, "Take" + 0)));
    }

    /**
     * Searches every searchable shop (or every shop in edit mode) for a trade with a matching item.
     * <p>
     * The search runs on an async thread while a reload may rewrite the registry and trade lists, so the
     * registry is snapshotted and each trade list is walked by index: a concurrent reload can neither throw
     * {@link java.util.ConcurrentModificationException} nor stop the search half way.
     *
     * @param mode     shop mode; {@link ShopMode#EDIT} searches shops that are not searchable as well
     * @param giveSide whether to match the items the trade gives ({@code true}) or takes
     * @param matches  tests the item at {@code index} of the trade's items
     */
    private static LinkedHashMap<ShopTrade, Shop> searchTrades(ShopMode mode, boolean giveSide,
                                                               BiPredicate<ObjectItems, Integer> matches) {
        LinkedHashMap<ShopTrade, Shop> trades = new LinkedHashMap<>();
        for (Shop shop : new ArrayList<>(ShopUtil.getShops().values())) {
            if (!shop.isSearchable() && !mode.equals(ShopMode.EDIT)) continue;
            List<ShopTrade> shopTrades = shop.getTrades();
            for (int t = 0; t < shopTrades.size(); t++) {
                ShopTrade trade = shopTrades.get(t);
                if (trade == null) continue;
                ObjectItems items = giveSide ? trade.getGiveData() : trade.getTakeData();
                for (int i = 0; i < items.size(); i++) {
                    if (matches.test(items, i)) {
                        trades.put(trade, shop);
                        break;
                    }
                }
            }
        }
        return trades;
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromGive(ItemStack item, ShopMode mode) {
        return searchTrades(mode, true, (items, i) -> items.isSimilar(i, item));
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromTake(ItemStack item, ShopMode mode) {
        return searchTrades(mode, false, (items, i) -> items.isSimilar(i, item));
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromGiveByDisplayName(String name, ShopMode mode) {
        return searchTrades(mode, true, (items, i) -> JavaUtil.containsIgnoreCase(items.getDisplayName(i), name));
    }

    public static LinkedHashMap<ShopTrade, Shop> getTradesFromTakeByDisplayName(String name, ShopMode mode) {
        return searchTrades(mode, false, (items, i) -> JavaUtil.containsIgnoreCase(items.getDisplayName(i), name));
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
