package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopEditorGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopGui2to1;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopGui4to4;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopGui6to2;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.trade.ShopTradeGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.TradeOption;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.ObjectItems;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityNBTBuilder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EquipmentUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;
import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Shop {
    @Getter
    protected UUID uuid;
    protected Entity hologram;
    @Getter
    protected EntityNBTBuilder NBTBuilder;
    @Getter
    protected String displayName;
    @Getter
    @Setter
    protected Location location;
    @Getter
    protected String mythicmob;
    @Getter
    protected UUID citizen;
    protected String entityType;
    @Getter
    protected NpcType npcType;
    protected ShopType type;
    @Getter
    protected List<ShopTrade> trades = new ArrayList<>();
    protected ConfigurationSection shopkeepersConfig;
    @Setter
    @Getter
    protected boolean lock = false;
    @Setter
    @Getter
    protected boolean searchable = false;
    @Setter
    @Getter
    protected boolean invisible = false;
    @Setter
    @Getter
    protected boolean editting = false;
    protected List<ShopEditorGui> editors = new ArrayList<>();
    protected List<ShopTradeGui> pages = new ArrayList<>();
    protected ObjectItems equipments;

    public Shop(Location location, String entityType, ConfigurationSection config) {
        this.shopkeepersConfig = config;
        initialize(location, () -> {
            this.entityType = entityType;
            this.npcType = entityType.equalsIgnoreCase("BLOCK") ? NpcType.BLOCK : NpcType.NORMAL;
        }, () -> {});
    }

    public Shop(Location location, UUID uuid, boolean load) {
        initialize(location, () -> {}, () -> {
            this.uuid = uuid;
            this.citizen = uuid;
            this.npcType = NpcType.CITIZEN;
        });
    }

    public Shop(Location location, String mmid) {
        initialize(location, () -> {}, () -> {
            this.mythicmob = mmid;
            this.npcType = NpcType.MYTHICMOB;
        });
    }

    private void initialize(Location location, Runnable beforeInitializer, Runnable afterInitializer) {
        boolean existed = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/" + LocationUtil.toStringFromLocation(location) + ".yml").exists();
        this.location = location;
        ShopUtil.addShop(getID(), this);
        beforeInitializer.run();
        loadYamlProcess(getFile());
        afterInitializer.run();
        if (!existed) {
            createEditorNewPage();
            saveYaml();
        }
    }

    public void loadYamlProcess(File file) {
        ShopSerializer.load(this, file);
        // Apply Shopkeepers config if present
        if (shopkeepersConfig != null) {
            ShopSerializer.applyShopkeepersConfig(this, shopkeepersConfig);
            shopkeepersConfig = null;
        }
    }

    public void updateTradeContents() {
        setTradePages();
        setEditors();
    }

    public void createNewPage() {
        createTradeNewPage();
        createEditorNewPage();
    }

    public void changeShopType() {
        if (!type.equals(ShopType.TwotoOne)) trades.clear();
        this.type = type.getNextShopType();
        updateTradeContents();
    }

    // 重複している取引があればtrueを返す
    public boolean checkTrades(Inventory inv) {
        ShopHolder holder = ShopUtil.getShopHolder(inv);
        if (holder == null) return false;
        ShopTradeGui gui = getPage(holder.getGui().getPage());
        if (gui == null) return false;

        //取引を上書きし、取引として成立しないものと重複しているものは削除する
        boolean duplication = false;
        HashSet<ShopTrade> emptyTrades = new HashSet<>();
        List<ShopTrade> onTrades = new ArrayList<>(getTrades());
        gui.getTrades().forEach(onTrades::remove);
        for (int i = 0; i < 9 * 6; i += getShopType().getAddSlot()) {
            if (getShopType().equals(ShopType.TwotoOne) && i % 9 == 4) i++;

            // 取引のオプションのスロットを取得する
            int optionSlot = i + getShopType().getSubtractSlot();

            ShopTrade trade = gui.getTradeFromSlot(i);
            ShopTrade expectedTrade = TradeUtil.getTrade(inv, i, getShopType());
            TradeOption option = TradeOption.getOption(inv.getItem(optionSlot));
            boolean available = expectedTrade != null;
            // 編集画面上に重複した取引が存在するかチェックする
            if (available && onTrades.contains(expectedTrade)) duplication = true;
            onTrades.add(expectedTrade);

            // 取引を追加、上書き、削除する
            if (trade == null && available) {
                // 取引を追加
                trades.add(expectedTrade);
                expectedTrade.setTradeOption(option, false);
                LogUtil.log(LogUtil.LogType.ADDTRADE, inv.getViewers().get(0).getName(), getID(), expectedTrade, expectedTrade.getLimit());
            } else if (available) {
                // 取引を上書き
                if (!(trade.equals(expectedTrade) && trade.getOption().equals(option)))
                    LogUtil.log(LogUtil.LogType.REPLACETRADE, inv.getViewers().get(0).getName(), getID(), trade, expectedTrade, trade.getOption(), expectedTrade.getOption());
                trade.setTrade(expectedTrade);
                trade.setTradeOption(option, true);
            } else if (trade != null) {
                // 取引を削除する
                emptyTrades.add(trade);
                LogUtil.log(LogUtil.LogType.REMOVETRADE, inv.getViewers().get(0).getName(), getID(), trade, trade.getLimit());
            }
        }
        this.trades.removeAll(emptyTrades);

        if (duplication) this.trades = trades.stream().distinct().collect(Collectors.toList());

        //ショップを更新する
        updateTradeContents();
        return duplication;
    }

    public ShopTrade getTrade(Inventory inv, int slot) {
        if (!((ShopTradeGui) ShopUtil.getShopHolder(inv).getGui()).isConvertSlot(slot)) return null;
        return TradeUtil.getTrade(inv, slot - type.getSubtractSlot(), type);
    }

    public HashMap<String, String> convertTradesToMap() {
        HashMap<String, String> trades = new HashMap<>();
        trades.put("ShopType", type.toString());
        trades.put("TradesSize", String.valueOf(this.trades.size()));
        for (int i = 0; i < this.trades.size(); i++) {
            trades.put("Give" + i, ItemUtil.toStringFromItemStackArray(this.trades.get(i).getGiveItems()));
            trades.put("Take" + i, ItemUtil.toStringFromItemStackArray(this.trades.get(i).getTakeItems()));
        }
        return trades;
    }

    public HashMap<String, String> convertOneTradeToMap(Inventory inv, int slot) {
        ShopTrade trade = getTrade(inv, slot);
        if (trade == null) return null;

        HashMap<String, String> trades = new HashMap<>();
        trades.put("ShopType", type.toString());
        trades.put("TradesSize", String.valueOf(1));
        trades.put("Give" + 0, ItemUtil.toStringFromItemStackArray(trade.getGiveItems()));
        trades.put("Take" + 0, ItemUtil.toStringFromItemStackArray(trade.getTakeItems()));
        return trades;
    }

    public String convertShopToString() {
        YamlConfiguration yaml = saveYaml();
        yaml.set("Trades", null);
        return saveYaml().saveToString();
    }

    public HashMap<String, String> convertShopToMap(HashMap<String, String> trades) {
        HashMap<String, String> shop = new HashMap<>();
        shop.put("ShopData", convertShopToString());
        shop.putAll(trades);
        return shop;
    }

    public ItemStack convertShopToItemStack() {
        ItemStack item = ItemUtil.getNamedEnchantedItem(Material.DIAMOND, ChatColor.AQUA + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_NAME.getMessage() + ChatColor.GREEN + getDisplayNameOrElseNone(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_CLICK.getMessage() + ChatColor.GREEN + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_MEARGE.getMessage(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_PLACELORE.getMessage() + ChatColor.GREEN + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_PLACE.getMessage(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_TYPE.getMessage() + type.getShopTypeDisplay()
        );
        ItemUtil.withItemInfo(item, getTrades().stream().limit(5).map(ShopTrade::getFirstGiveTakeItem).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)));
        item = NBTUtil.setNMSTag(item, convertShopToMap(convertTradesToMap()));
        return item;
    }

    public ItemStack convertShopToItemStack(Inventory inv, int slot) {
        ItemStack item = ItemUtil.getNamedEnchantedItem(Material.DIAMOND, ChatColor.AQUA + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_NAME.getMessage() + ChatColor.GREEN + getDisplayNameOrElseNone(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_CLICK.getMessage() + ChatColor.GREEN + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_MEARGE.getMessage(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_PLACELORE.getMessage() + ChatColor.GREEN + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_PLACE.getMessage(),
                                                        ChatColor.YELLOW + LanguageKey.ITEM_SHOP_COMPRESSION_GEM_TYPE.getMessage() + type.getShopTypeDisplay()
        );

        ShopTrade trade = getTrade(inv, slot);
        if (trade == null) return null;

        ItemUtil.withItemInfo(item, new LinkedHashMap<>(Map.of(trade.getFirstGiveTakeItem().getKey(), trade.getFirstGiveTakeItem().getValue())));
        item = NBTUtil.setNMSTag(item, convertShopToMap(convertOneTradeToMap(inv, slot)));
        return item;
    }

    public boolean loadTrades(ItemStack item, Player p) {
        ShopType type = ShopType.valueOf(NBTUtil.getNMSStringTag(item, "ShopType"));
        if(!getShopType().equals(type) && !getShopType().equals(ShopType.TwotoOne)) return false;
        List<ShopTrade> temp = TradeUtil.convertTradesToList(item);
        if (temp == null) return false;
        boolean duplication = temp.stream().anyMatch(trade -> trades.contains(trade));
        trades.addAll(temp);
        temp.forEach(trade -> LogUtil.log(LogUtil.LogType.ADDTRADE, p.getName(), getID(), trade, trade.getLimit()));
        if (duplication) trades = trades.stream().distinct().collect(Collectors.toList());
        updateTradeContents();
        return duplication;
    }

    public void removeShop() {
        removeNPC();
        if (npcType.equals(NpcType.CITIZEN)) CitizensHandler.destoryNPC(this);
        if (hologram != null) hologram.remove();
        getFile().delete();
        ShopUtil.removeShop(getID());
    }

    public void removeShop(Player p) {
        LogUtil.log(LogUtil.LogType.REMOVESHOP, p.getName(), getID());
        removeShop();
    }

    public List<ShopTrade> getTrades(int page) {
        List<ShopTrade>[] trades = JavaUtil.splitList(getTrades(), type.getLimitSize());
        if (trades.length == page - 1) return new ArrayList<>();
        return trades[page - 1];
    }

    public void setTrades(List<ShopTrade> trades) {
        this.trades = trades;
        updateTradeContents();
    }

    public void addAllTrades(List<ShopTrade> trades) {
        this.trades.addAll(trades);
        this.trades = this.trades.stream().distinct().collect(Collectors.toList());
        updateTradeContents();
    }

    public String getID() {
        return LocationUtil.toStringFromLocation(location);
    }
    public Entity getEntity() {
        if(uuid == null) return null;
        return Bukkit.getEntity(uuid);
    }

    public ShopTradeGui getPage(int page) {
        if (page <= 0) return null;
        if (page > pages.size()) return null;
        return pages.get(page - 1);
    }

    public int getPage(ShopTrade trade) {
        if (!trades.contains(trade)) return -1;
        return (int) Math.ceil((double) (trades.indexOf(trade) + 1) / type.getLimitSize());
    }

    public void setTradePages() {
        pages.clear();
        for (int i = 1; i <= getTradePageCountFromTradesCount(); i++) {
            switch (type) {
                case TwotoOne:
                    pages.add(new ShopGui2to1(this, i));
                    break;
                case FourtoFour:
                    pages.add(new ShopGui4to4(this, i));
                    break;
                case SixtoTwo:
                    pages.add(new ShopGui6to2(this, i));
                    break;
            }
        }
    }

    public ShopEditorGui getEditor(int page) {
        if (page <= 0) return null;
        if (page > editors.size()) return null;
        return editors.get(page - 1);
    }

    public void setEditors() {
        editors.clear();
        if (pages.isEmpty()) editors.add(new ShopEditorGui(this, 1));
        for (int i = 1; i <= getEditorPageCountFromTradesCount(); i++) {
            editors.add(new ShopEditorGui(this, i));
        }
        HashMap<String, List<Player>> map = new HashMap<>();
        if (ableCreateEditorNewPage())
            editors.add(new ShopEditorGui(this, getEditorPageCountFromTradesCount() + 1));
    }

    public boolean isLimitPage(int page) {
        return ShopPageCalculator.isPageFull(getPage(page).getTrades().size(), type.getLimitSize());
    }

    public int getPageCount() {
        return pages.size();
    }

    public int getTradePageCountFromTradesCount() {
        return ShopPageCalculator.calculateTradePageCount(trades.size(), type.getLimitSize());
    }

    public int getEditorPageCountFromTradesCount() {
        return ShopPageCalculator.calculateEditorPageCount(getTradePageCountFromTradesCount());
    }

    public ShopType getShopType() {
        return type;
    }

    public boolean ableCreateNewPage() {
        if (trades.isEmpty()) return true;
        return isLimitPage(pages.size());
    }
    // ableCreateNewPage kept as-is because it uses isLimitPage which now delegates to ShopPageCalculator

    public void createTradeNewPage() {
        if (!ableCreateNewPage()) return;
        switch (type) {
            case TwotoOne:
                pages.add(new ShopGui2to1(this, getPageCount() + 1));
                break;
            case FourtoFour:
                pages.add(new ShopGui4to4(this, getPageCount() + 1));
                break;
            case SixtoTwo:
                pages.add(new ShopGui6to2(this, getPageCount() + 1));
                break;
        }
    }

    public boolean ableCreateEditorNewPage() {
        return ShopPageCalculator.canCreateNewEditorPage(
                editors.isEmpty(), editors.size(), getEditorPageCountFromTradesCount());
    }

    public void createEditorNewPage() {
        if (!ableCreateEditorNewPage()) return;
        editors.add(new ShopEditorGui(this, getPageCount() + 1));
    }

    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return ShopSerializer.getSaveYamlProcess(this);
    }

    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return ShopSerializer.getLoadYamlProcess(this);
    }

    public YamlConfiguration saveYaml() {
        return ShopSerializer.save(this);
    }

    public File getFile() {
        return ShopSerializer.getFile(this);
    }

    public String getDisplayNameOrElseShop() {
        return JavaUtil.getOrDefault(displayName, LanguageKey.INVENTORY_DEFAULT_SHOP.getMessage());
    }

    public void setDisplayName(String name) {
        this.displayName = name;
        Entity npc = getEntity();
        if (npc != null) npc.setCustomName(name);
        if ("BLOCK".equalsIgnoreCase(entityType)) {
            if (hologram != null) hologram.remove();
            hologram = EntityUtil.spawnHologram(location.clone().add(0.5, 1, 0.5), displayName);
        }
    }

    public boolean containsDisplayName(String name) {
        return JavaUtil.containsIgnoreCase(displayName, name);
    }

    public String getDisplayNameOrElseNone() {
        return JavaUtil.getOrDefault(displayName, ChatColor.YELLOW + "<none>");
    }

    private void spawnNPC(EntityType entityType) {
        if(getEntity() != null) return;
        this.location.setPitch(0);
//        this.npc = EntityUtil.spawnEntity(LocationUtil.toBlockLocationFromLocation(location), entityType);
        Entity npc = EntityUtil.spawnEntity(LocationUtil.getMiddleLocation(location), entityType);
        this.uuid = npc.getUniqueId();
        npc.teleport(LocationUtil.toBlockLocationFromLocation(location));
        setNpcMeta(npc);
    }

    public void setNpcMeta(Entity npc) {
        ShopNPCManager.setNpcMeta(this, npc);
    }

    public void setNpcMetaFromShopkeepersConfiguration(ConfigurationSection section) {
        if (this instanceof AgeableShop)
            ((AgeableShop) this).setAgeLook(!section.getBoolean("baby", false));
        if (this instanceof PoweredableShop)
            ((PoweredableShop) this).setPowered(section.getBoolean("powered", false));
        if (this instanceof HorseShop) {
            ((HorseShop) this).setColor(Horse.Color.valueOf(section.getString("color", "WHITE")));
            ((HorseShop) this).setStyle(Horse.Style.valueOf(section.getString("style", "NONE")));
        }
        if (this instanceof VillagerableShop) {
            IVillagerHandler handler = VillagerHandlerProvider.getHandler();
            String professionName = section.getString("profession", section.getString("prof", handler.getDefaultProfessionName()));
            ((VillagerableShop) this).setProfession(Villager.Profession.valueOf(handler.resolveProfession(professionName)));
            ((VillagerableShop) this).setBiome(Villager.Type.valueOf(handler.resolveBiome(section.getString("villagerType", handler.getDefaultBiomeName()))));
            ((VillagerableShop) this).setLevel(section.getInt("villagerLevel", 1));
        }
        if (this instanceof ParrotShop)
            ((ParrotShop) this).setColor(Parrot.Variant.valueOf(section.getString("parrotVariant", "RED")));
        if (this instanceof DyeableShop) {
            String color;
            try {
                Integer.parseInt(section.getString("color", "WHITE"));
                color = DyeColor.values()[section.getInt("color", 0)].name();
            } catch (NumberFormatException e) {
                color = section.getString("color", "WHITE");
            }
            ((DyeableShop) this).setColor(DyeColor.valueOf(color));
            ((DyeableShop) this).setOptionalInfo(
                    (
                            section.contains("angry") ? section.getBoolean("angry", false) :
                                    (section.contains("sitting") ? section.getBoolean("sitting", false) :
                                            (section.getBoolean("shaved", false)))
                    )
            );
        }
    }

    public void initializeLivingEntitiy(Entity npc) {
        ShopNPCManager.initializeLivingEntity(npc);
    }

    public void changeInvisible() {
        ShopNPCManager.changeInvisible(this);
    }

    public void changeNPCDirection() {
        ShopNPCManager.changeNPCDirection(this);
    }

    public ItemStack getEquipmentItem(int slot) {
        return equipments.toItemStacks()[slot];
    }

    public void setEquipmentItem(ItemStack item, int slot) {
        equipments.setObject(item, slot);
        updateEquipments();
    }

    public ItemStack getEquipmentDisplayItem(EquipmentSlot slot) {
        return JavaUtil.getOrDefault(getEquipmentItem(slot.ordinal()), EquipmentUtil.getEquipmentDisplayItem(slot));
    }

    public void updateEquipments() {
        ShopNPCManager.updateEquipments(this);
    }

    public boolean isEditting(Player p) {
        if (isEditting()) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_EDITING.getMessage());
            SoundUtil.playFailSound(p);
            return true;
        }
        return false;
    }

    public boolean isSearchable(Player p) {
        if (!isSearchable() && !p.hasPermission("sis.op")) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_UNSEARCHABLE.getMessage());
            SoundUtil.playFailSound(p);
            return false;
        }
        return true;
    }

    public boolean isLock(Player p) {
        if (isLock() && !p.hasPermission("sis.op")) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_LOCKED.getMessage());
            SoundUtil.playFailSound(p);
            return true;
        }
        return false;
    }

    public boolean isLockSilent(Player p) {
        return isLock() && !p.hasPermission("sis.op");
    }

    public boolean isEmpty(Player p) {
        if (pages.isEmpty()) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_NO_TRADES.getMessage());
            SoundUtil.playFailSound(p);
            return true;
        }
        return false;
    }

    public boolean isOpenableShop(Player p) {
        return (!isLock() || isSearchable(p)) && !isEditting(p) && !isEmpty(p);
    }

    public void setNpcType(String entityType) {
        removeNPC();
        this.npcType = "BLOCK".equalsIgnoreCase(entityType) ? NpcType.BLOCK : NpcType.NORMAL;
        this.entityType = entityType;
        this.mythicmob = null;
        this.uuid = null;
        this.citizen = null;
    }

    public void setMythicType(String mythicType) {
        removeNPC();
        this.npcType = NpcType.MYTHICMOB;
        this.mythicmob = mythicType;
        this.uuid = null;
        this.entityType = null;
        this.citizen = null;
    }

    public void setCitizen(Entity entity) {
        removeNPC();
        this.npcType = NpcType.CITIZEN;
        this.uuid = CitizensHandler.getNpcUUID(entity);
        this.citizen = CitizensHandler.getNpcUUID(entity);
        this.mythicmob = null;
        this.entityType = null;
    }

    public void setBlock() {
        removeNPC();
        this.npcType = NpcType.BLOCK;
        this.entityType = "BLOCK";
        this.mythicmob = null;
        this.uuid = null;
        this.citizen = null;
    }

    public void removeNPC() {
        ShopNPCManager.removeNPC(this);
    }

    public void respawnNPC() {
        ShopNPCManager.respawnNPC(this);
    }

    protected boolean isEditableNpc() {
        return npcType.equals(NpcType.NORMAL);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Shop) {
            Shop shop = (Shop) obj;
            return shop.getID().equals(getID());
        }
        return false;
    }
}
