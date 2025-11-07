package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.DisplayPanelConfig;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.ShopType;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.ObjectItems;
import ryuzuinfiniteshop.ryuzuinfiniteshop.event.TradeCompleteEvent;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.VaultHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

@EqualsAndHashCode
public class ShopTrade {
    public static final BiMap<ShopTrade, UUID> tradeUUID = HashBiMap.create();
    public static final Table<UUID, UUID, Integer> tradeCounts = HashBasedTable.create();
    public static final HashMap<UUID, TradeOption> tradeOptions = new HashMap<>();
    private static final Random random = new Random();

    public enum TradeResult {NotEnoughMoney, NotEnoughItems, Fail, Full, Success, Lack, Limited, Locked, Error, Normal}

    private ObjectItems giveData;
    private ObjectItems takeData;

    public ConfigurationSection serialize() {
        ConfigurationSection config = new MemoryConfiguration();
        config.set("give", giveData.getObjects());
        config.set("take", takeData.getObjects());
        if (tradeUUID.containsKey(this))
            config.set("uuid", tradeUUID.get(this).toString());
        return config;
    }

    public ShopTrade(HashMap<String, Object> config) {
        this.giveData = new ObjectItems(config.get("give"));
        this.takeData = new ObjectItems(config.get("take"));
        if (config.containsKey("uuid"))
            tradeUUID.put(this, UUID.fromString((String) config.get("uuid")));
    }

    public ShopTrade(ItemStack[] give, ItemStack[] take) {
        this.giveData = new ObjectItems(give);
        this.takeData = new ObjectItems(take);
    }

    public ItemStack[] getGiveItems() {
        return giveData.toItemStacks();
    }

    public ItemStack[] getTakeItems() {
        return takeData.toItemStacks();
    }

    public Map.Entry<ItemStack, ItemStack> getFirstGiveTakeItem() {
        return new AbstractMap.SimpleEntry<>(takeData.toItemStacks()[0], giveData.toItemStacks()[0]);
    }

    public int getLimit() {
        if (!tradeUUID.containsKey(this)) return 0;
        return tradeOptions.getOrDefault(tradeUUID.get(this), new TradeOption()).getLimit();
    }

    public TradeOption getOption() {
        if (!tradeUUID.containsKey(this)) return new TradeOption();
        return tradeOptions.getOrDefault(tradeUUID.get(this), new TradeOption());
    }

    public Integer getTradeCount(Player player) {
        if (player == null) return 0;
        int count = 0;
        if (!tradeUUID.containsKey(this)) return 0;
        return tradeCounts.contains(player.getUniqueId(), tradeUUID.get(this)) ? tradeCounts.get(player.getUniqueId(), tradeUUID.get(this)) : 0;
    }

    public void addTradeCount(Player player) {
        if (!tradeUUID.containsKey(this)) return;
        if (getOption().getLimit() == 0) return;
        tradeCounts.put(player.getUniqueId(), tradeUUID.get(this), getTradeCount(player) + 1);
    }

    public void setTradeCount(Player player, int count) {
        if (!tradeUUID.containsKey(this)) return;
        if (getOption().getLimit() == 0) return;
        tradeCounts.put(player.getUniqueId(), tradeUUID.get(this), count);
    }

    public void saveTradeOption() {
        if (getOption().isNoData()) return;
        File file = FileUtil.initializeFile("options.yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        saveTradeOption(config);

        try {
            config.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }
    }

    public void saveTradeOption(YamlConfiguration config) {
        if (getOption().isNoData()) return;
        UUID tradeID = tradeUUID.get(this);
        config.set(tradeID.toString() + ".options", getOption());
        ShopTrade.tradeCounts.rowKeySet().forEach(playerID -> config.set(tradeID + ".counts." + playerID.toString(), ShopTrade.tradeCounts.get(playerID, tradeID)));
    }

    public static ItemStack getFilter() {
        return ItemUtil.getNamedItem(ItemUtil.getColoredItem("BLACK_STAINED_GLASS_PANE"), ChatColor.BLACK + "");
    }

    public ItemStack getFilter(ShopMode mode) {
        return mode.equals(ShopMode.EDIT) ? getSettingsFilter() : getFilter();
    }

    public static ItemStack getFilterNoData(ShopMode mode) {
        return mode.equals(ShopMode.EDIT) ? getSettingsFilterNoData() : getFilter();
    }

    public ItemStack getFilter(String id, Player player) {
        Shop shop = ShopUtil.getShop(id);
        String page = String.valueOf(shop.getPage(this));
        TradeResult result = getResult(player, shop);
        boolean isAdmin = player.hasPermission("sis.op");
        return NBTUtil.setNMSTag(
                NBTUtil.setNMSTag(
                        ItemUtil.getNamedItem(
                                DisplayPanelConfig.getPanel(result).getItemStack(),
                                ChatColor.GREEN + LanguageKey.ITEM_TRADE_WITH.getMessage(ShopUtil.getShop(id).getDisplayNameOrElseNone()),
                                false,
                                ChatColor.YELLOW + LanguageKey.INVENTORY_PAGE.getMessage(page),
                                isAdmin ? (ChatColor.YELLOW + LanguageKey.ITEM_EDITOR_IS_SEARCHABLE.getMessage(shop.isSearchable() ? ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SEARCH_ENABLED.getMessage() : ChatColor.RED + LanguageKey.ITEM_EDITOR_SEARCH_DISABLED.getMessage())) : null,
                                isAdmin ? (ChatColor.YELLOW + LanguageKey.ITEM_IS_LOCKED.getMessage(shop.isLock() ? ChatColor.RED + LanguageKey.ITEM_EDITOR_LOCKED.getMessage() : ChatColor.GREEN + LanguageKey.ITEM_EDITOR_UNLOCKED.getMessage())) : null,
                                ChatColor.GREEN + LanguageKey.ITEM_TRADE_WINDOW_OPEN.getMessage(),
                                isAdmin ? ChatColor.GREEN + LanguageKey.ITEM_EDIT_WINDOW_OPEN.getMessage() : null
                        ),
                        "Shop", id
                ), "Page", page
        );
    }

    private ItemStack getSettingsFilter() {
        return ItemUtil.withLore(
                getOption().getOptionsPanel(DisplayPanelConfig.getPanel(TradeResult.Normal).getItemStack()),
                true,
                ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_TRADE_SET_OPTION.getMessage(),
                ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_TRADE_TO_ITEM.getMessage()
        );
    }

    private static ItemStack getSettingsFilterNoData() {
        return ItemUtil.withLore(
                DisplayPanelConfig.getPanel(TradeResult.Normal).getItemStack(),
                true,
                ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_TRADE_SET_OPTION.getMessage(),
                ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_TRADE_TO_ITEM.getMessage()
        );
    }

    public void setTradeOption(TradeOption option, boolean force) {
        if (option.isNoData() && !force) return;
        if (option.isNoData() && force && tradeUUID.containsKey(this)) {
            tradeOptions.remove(tradeUUID.get(this));
            tradeUUID.remove(this);
        } else {
            UUID uuid = tradeUUID.computeIfAbsent(this, key -> UUID.randomUUID());
            tradeOptions.put(uuid, option);
        }
    }

//    public void setTradeOption(TradeOption option, boolean force) {
//        TradeOption data = tradeOptions.computeIfAbsent(tradeUUID.computeIfAbsent(this, key -> UUID.randomUUID()), key -> new TradeOption());
//        if (force)
//            data.setLimit(limit);
//        else
//            data.setLimit(Math.max(data.getLimit(), limit));
//        if (data.isNoData()) {
//            tradeOptions.remove(tradeUUID.get(this));
//            tradeUUID.remove(this);
//        }
//    }

    public ItemStack[] getTradeItems(ShopType type, ShopMode mode) {
        return getTradeItems(type, mode, false);
    }

    public ItemStack[] getTradeItems(ShopType type, ShopMode mode, boolean explain) {
        ItemStack[] items = new ItemStack[type.getAddSlot()];
        ItemStack filter = getFilter(mode);
        items[type.getSubtractSlot()] = filter;
        for (int i = 0; i < getTakeItems().length; i++)
            items[i] = getTakeItems()[i];
        for (int i = 0; i < getGiveItems().length; i++)
            items[type.getSubtractSlot() + 1 + i] = getGiveItems()[i];
        return items;
    }

    public ItemStack[] getTradeItems(ShopType type) {
        return getTradeItems(type, getFilter(ShopMode.EDIT));
    }

    public ItemStack[] getTradeItems(ShopType type, String id, Player player) {
        return getTradeItems(type, getFilter(id, player));
    }

    public ItemStack[] getTradeItems(ShopType type, ItemStack filter) {
        ItemStack[] items = new ItemStack[9];
        items[(type.equals(ShopType.SixtoTwo) ? 6 : 4)] = filter;
        for (int i = 0; i < getTakeItems().length; i++)
            items[i] = getTakeItems()[i];
        for (int i = 0; i < getGiveItems().length; i++)
            items[(type.equals(ShopType.SixtoTwo) ? 6 : 4) + 1 + i] = getGiveItems()[i];
        return items;
    }

    public void setTrade(ShopTrade trade) {
        if (trade == null) return;
        this.takeData = trade.takeData;
        this.giveData = trade.giveData;
    }

    public TradeResult getResult(Player p) {
        TradeResult result = TradeResult.Success;

        if (!affordItem(p)) result = TradeResult.NotEnoughItems;
        else if (!affordMoney(p)) result = TradeResult.NotEnoughMoney;
        else if (isError()) result = TradeResult.Error;
        else if (isLimited(p)) result = TradeResult.Limited;
        else if (!hasEnoughSpace(p)) result = TradeResult.Full;
        return result;
    }

    public TradeResult getResult(Player p, Shop shop) {
        TradeResult result = getResult(p);
        if (shop.isLockSilent(p)) result = TradeResult.Locked;

        return result;
    }

    private TradeResult trade(Player p) {
        Inventory inv = p.getInventory();
        TradeResult result = getResult(p);

        //アイテムを追加する
        if (result == TradeResult.Success) {
            inv.removeItem(getTakeItems());
            if (getOption().getMoney() != 0 && !getOption().isGive())
                VaultHandler.takeMoney(p.getUniqueId(), getOption().getMoney());
            if (getOption().getRate() == 100 || getOption().getRate() > random.nextInt(100) + 1) {
                if (getOption().getMoney() != 0 && getOption().isGive())
                    VaultHandler.giveMoney(p.getUniqueId(), getOption().getMoney());
                inv.addItem(getGiveItems());
            } else
                result = TradeResult.Fail;
            addTradeCount(p);
            Bukkit.getPluginManager().callEvent(new TradeCompleteEvent(
                    this,
                    p,
                    result
            ));
        }
        return result;
    }

    public int trade(Player p, int times) {
        TradeResult result = getResult(p);
        int resultTime = times;
        for (int time = 0; time < times; time++) {
            result = trade(p);
            if (!result.equals(TradeResult.Success)) {
                if (time != 0) {
                    if(times == 64 * 48)
                        result = TradeResult.Lack;
                    else
                        result = TradeResult.Success;
                }
                resultTime = time;
                break;
            }
        }
        playResultEffect(p, result);
        saveTradeOption();
        return resultTime;
    }

    //アイテムを追加できるかチェックする
    private boolean hasEnoughSpace(Player p) {
        return ItemUtil.ableGive(p.getInventory(), getGiveItems());
    }

    //アイテムを所持しているか確認する
    private boolean affordItem(Player p) {
        return ItemUtil.contains(p.getInventory(), getTakeItems());
    }

    private boolean affordMoney(Player p) {
        if (getOption().getMoney() == 0) return true;
        return VaultHandler.hasMoney(p.getUniqueId(), getOption().getMoney());
    }

    private boolean isError() {
        return Arrays.stream(getGiveItems()).anyMatch(item -> NBTUtil.getNMSStringTag(item, "Error") != null) ||
                Arrays.stream(getTakeItems()).anyMatch(item -> NBTUtil.getNMSStringTag(item, "Error") != null);
    }

    private boolean isLimited(Player p) {
        if (getLimit() == 0) return false;
        return getTradeCount(p) >= getLimit();
    }

    public static void playResultEffect(Player p, TradeResult result) {
        switch (result) {
            case NotEnoughItems:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_ENOUGH_ITEMS.getMessage());
                SoundUtil.playFailSound(p);
                break;
            case NotEnoughMoney:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_ENOUGH_MONEY.getMessage());
                SoundUtil.playFailSound(p);
                break;
            case Locked:
//                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_LOCKED.getMessage());
//                SoundUtil.playFailSound(p);
                break;
            case Error:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.COMMAND_INVALID_TRADE.getMessage());
                SoundUtil.playFailSound(p);
                break;
            case Fail:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.ESSAGE_ERROR_TRADE_FAILED.getMessage());
                SoundUtil.playBreakSound(p);
                break;
            case Limited:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_TRADE_LIMITED.getMessage());
                SoundUtil.playFailSound(p);
                break;
            case Lack:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_BOUGHT_EVERYTHING.getMessage());
                SoundUtil.playCautionSound(p);
                break;
            case Full:
                p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_INVENTORY_FULL.getMessage());
                SoundUtil.playCautionSound(p);
                break;
            case Success:
                SoundUtil.playClickShopSound(p);
                break;
        }
    }
}
