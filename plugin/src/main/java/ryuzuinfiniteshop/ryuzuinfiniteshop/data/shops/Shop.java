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
        boolean exsited = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/" + LocationUtil.toStringFromLocation(location) + ".yml").exists();
        this.location = location;
        ShopUtil.addShop(getID(), this);
        beforeInitializer.run();
        loadYamlProcess(getFile());
        afterInitializer.run();
        if (!exsited) {
            createEditorNewPage();
            saveYaml();
        }
    }

    public void loadYamlProcess(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        getLoadYamlProcess().accept(config);
    }

    protected Consumer<YamlConfiguration> getLoadYamlProcess() {
        return yaml -> {
            this.mythicmob = yaml.getString("Npc.Options.MythicMob");
            if (mythicmob != null) {
                this.npcType = NpcType.MYTHICMOB;
                if(MythicInstanceProvider.isLoaded() && !MythicInstanceProvider.getInstance().exsistsMythicMob(mythicmob))
                    new RuntimeException(LanguageKey.ERROR_MYTHICMOBS_INVALID_ID.getMessage(mythicmob)).printStackTrace();
            }
            String citizenId = yaml.getString("Npc.Options.Citizen");
            if (citizenId != null) {
                this.uuid = UUID.fromString(citizenId);
                this.citizen = uuid;
                this.npcType = NpcType.CITIZEN;
                if (CitizensHandler.isLoaded() && !CitizensHandler.isCitizensNPC(uuid))
                    new RuntimeException(LanguageKey.ERROR_MYTHICMOBS_INVALID_ID.getMessage(uuid.toString())).printStackTrace();
            }
            this.displayName = yaml.getString("Npc.Options.DisplayName");
            this.invisible = yaml.getBoolean("Npc.Options.Invisible", false);
            this.location.setYaw((float) yaml.getDouble("Npc.Status.Yaw", 0));
            this.type = ShopType.valueOf(yaml.getString("Shop.Options.ShopType", "TwotoOne"));
            this.lock = yaml.getBoolean("Npc.Status.Lock", false);
            this.searchable = yaml.getBoolean("Npc.Status.Searchable", true);
            this.equipments = new ObjectItems(yaml.get("Npc.Options.Equipments", IntStream.range(0, 6).mapToObj(i -> new ItemStack(Material.AIR)).collect(Collectors.toList())));
            this.trades = yaml.getList("Trades", new ArrayList<>()).stream().map(tradeconfig -> new ShopTrade((HashMap<String, Object>) tradeconfig)).collect(Collectors.toList());
            updateTradeContents();

            if (shopkeepersConfig == null) return;
            setNpcMetaFromShopkeepersConfiguration(RyuZUInfiniteShop.VERSION < 14 ? shopkeepersConfig : shopkeepersConfig.getConfigurationSection("object"));
            setDisplayName(shopkeepersConfig.getString("name", "").isEmpty() ? "" : ChatColor.GREEN + shopkeepersConfig.getString("name"));
            shopkeepersConfig = null;
        };
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
        return getPage(page).getTrades().size() == type.getLimitSize();
    }

    public int getPageCount() {
        return pages.size();
    }

    public int getTradePageCountFromTradesCount() {
        int size = trades.size() / type.getLimitSize();
        if (trades.size() % type.getLimitSize() != 0) size++;
        return size;
    }

    public int getEditorPageCountFromTradesCount() {
        int size = getTradePageCountFromTradesCount() / 18;
        //if (getTradePageCountFromTradesCount() % 18 != 0) size++;
        return size + 1;
    }

    public ShopType getShopType() {
        return type;
    }

    public boolean ableCreateNewPage() {
        if (trades.isEmpty()) return true;
        return isLimitPage(pages.size());
    }

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
        if (editors.isEmpty()) return true;
        return editors.size() < getEditorPageCountFromTradesCount();
    }

    public void createEditorNewPage() {
        if (!ableCreateEditorNewPage()) return;
        editors.add(new ShopEditorGui(this, getPageCount() + 1));
    }

    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return yaml -> {
            yaml.set("Npc.Options.MythicMob", mythicmob);
            yaml.set("Npc.Options.Citizen", npcType.equals(NpcType.CITIZEN) ? citizen.toString() : null);
            yaml.set("Npc.Options.DisplayName", displayName);
            yaml.set("Npc.Options.EntityType", entityType);
            yaml.set("Npc.Options.Invisible", invisible);
            yaml.set("Shop.Options.ShopType", type.toString());
            yaml.set("Npc.Options.Equipments", equipments.getObjects());
            yaml.set("Npc.Status.Lock", lock);
            yaml.set("Npc.Status.Searchable", searchable);
            yaml.set("Trades", getTrades().stream().map(ShopTrade::serialize).collect(Collectors.toList()));
            yaml.set("Npc.Status.Yaw", location.getYaw());
        };
    }

    public YamlConfiguration saveYaml() {
        File file = getFile();
        YamlConfiguration yaml = new YamlConfiguration();
        getSaveYamlProcess().accept(yaml);
        try {
            yaml.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException)
                throw new RuntimeException(LanguageKey.ERROR_FILE_SAVING.getMessage(file.getName()), e);
        }
        return yaml;
    }

    public File getFile() {
        return FileUtil.initializeFile("shops/" + getID() + ".yml");
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
        if (npc == null) return;
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setGravity(false);
//        npc.setPersistent(false);
        npc = NBTUtil.setNMSTag(npc, "Shop", getID());
        initializeLivingEntitiy(npc);
        if (EntityType.END_CRYSTAL.name().equalsIgnoreCase(entityType))
            ((EnderCrystal) npc).setShowingBottom(false);
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
            ((VillagerableShop) this).setProfession(Villager.Profession.valueOf(section.getString(RyuZUInfiniteShop.VERSION < 14 ? "prof" : "profession", "FARMER")));
            if (RyuZUInfiniteShop.VERSION < 14) return;
            ((VillagerableShop) this).setBiome(Villager.Type.valueOf(section.getString("villagerType")));
            ((VillagerableShop) this).setLevel(section.getInt("villagerLevel"));
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
        if (!(npc instanceof LivingEntity)) return;
        LivingEntity livnpc = (LivingEntity) npc;
        livnpc.setAI(false);
        livnpc.setCollidable(false);
        livnpc.setRemoveWhenFarAway(true);
        livnpc.setPersistent(false);
//        if(RyuZUInfiniteShop.VERSION < 14) NBTBuilder.setNoAI(true);
//        NBTBuilder.setPersistenceRequired(true);
    }

    public void changeInvisible() {
        if (!(getEntity() instanceof LivingEntity)) return;
        NBTBuilder.setInvisible(!invisible);
        invisible = !invisible;
    }

    public void changeNPCDirecation() {
        Entity npc = getEntity();
        if (!(npc instanceof LivingEntity)) return;
        LivingEntity livnpc = (LivingEntity) npc;
        location.setYaw((location.getYaw() + 45));
        livnpc.teleport(LocationUtil.toBlockLocationFromLocation(location));
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
        if (!npcType.equals(NpcType.CITIZEN)) {
            Entity npc = getEntity();
            if (npc instanceof LivingEntity) {
                LivingEntity livnpc = ((LivingEntity) npc);
                for (EquipmentSlot slot : EquipmentUtil.getEquipmentsSlot().values()) {
                    switch (slot) {
                        case HAND:
                            livnpc.getEquipment().setItemInMainHand(getEquipmentItem(slot.ordinal()));
                            break;
                        case OFF_HAND:
                            livnpc.getEquipment().setItemInOffHand(getEquipmentItem(slot.ordinal()));
                            break;
                        case FEET:
                            livnpc.getEquipment().setBoots(getEquipmentItem(slot.ordinal()));
                            break;
                        case LEGS:
                            livnpc.getEquipment().setLeggings(getEquipmentItem(slot.ordinal()));
                            break;
                        case CHEST:
                            livnpc.getEquipment().setChestplate(getEquipmentItem(slot.ordinal()));
                            break;
                        case HEAD:
                            livnpc.getEquipment().setHelmet(getEquipmentItem(slot.ordinal()));
                            break;
                    }
                }
//                for (EquipmentSlot slot : EquipmentUtil.getEquipmentsSlot().values())
//                    livnpc.getEquipment().setItem(slot, getEquipmentItem(slot.ordinal()));
            }
        } else if (CitizensHandler.isLoaded() && CitizensHandler.isCitizensNPC(uuid)) {
            for (EquipmentSlot slot : EquipmentUtil.getEquipmentsSlot().values())
                CitizensHandler.setEquipment(uuid, slot, equipments.toItemStacks()[slot.ordinal()]);
            CitizensHandler.respawn(this);
        }
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

    /**
     *
     */
    public void removeNPC() {
        Entity npc = getEntity();
        if (npc != null) {
            NBTUtil.removeNMSTag(npc);
            npc.remove();
            Block block = location.clone().subtract(0, -1, 0).getBlock();
            location.getWorld().getNearbyEntities(LocationUtil.getMiddleLocation(location), 0.1, 0.1, 0.1).stream().forEach(Entity::remove);
//            Location pos = block.getBlockData() instanceof Slab && ((Slab) block.getBlockData()).getType().equals(Slab.Type.BOTTOM) ? location.clone().add(0, -0.5, 0) : location;
//            location.getWorld().getNearbyEntities(LocationUtil.getMiddleLocation(location), 0.3, 0.3, 0.3).stream().filter(entity -> NBTUtil.getNMSStringTag(entity, "Shop") != null).forEach(Entity::remove);
        }
        if (npcType.equals(NpcType.CITIZEN)) CitizensHandler.despawnNPC(this);
        uuid = null;
    }

    public void respawnNPC() {
        if (entityType == null && JavaUtil.isEmptyString(displayName)) return;
        if (FileUtil.isSaveBlock()) return;
        Entity npc = getEntity();
        if (npc != null && npc.isValid()) return;
        if (npc != null && !npc.isDead() && RyuZUInfiniteShop.VERSION < 14) return;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        removeNPC();

        switch (npcType) {
            case MYTHICMOB:
                if (!MythicInstanceProvider.isLoaded() || !MythicInstanceProvider.getInstance().exsistsMythicMob(mythicmob)) return;
                npc = MythicInstanceProvider.getInstance().spawnMythicMob(LocationUtil.getMiddleLocation(location), mythicmob);
                if(npc == null) return;
                this.uuid = npc.getUniqueId();
                npc = getEntity();
                setNpcMeta(npc);
                return;
            case CITIZEN:
                if (!CitizensHandler.isLoaded()) return;
                this.uuid = CitizensHandler.createNPC(this);
                this.citizen = uuid;
                CitizensHandler.spawnNPC(this);
                return;
            case BLOCK:
                if (hologram != null && hologram.isValid()) return;
                hologram = EntityUtil.spawnHologram(location.clone().add(0.5, 1, 0.5), displayName);
                return;
            default:
                spawnNPC(EntityType.valueOf(entityType));
                npc = getEntity();
                npc.setCustomName(displayName);
                npc.getPassengers().forEach(Entity::remove);
                Optional.ofNullable(npc.getVehicle()).ifPresent(Entity::remove);
                if (npc instanceof LivingEntity)
                    updateEquipments();

                this.NBTBuilder = new EntityNBTBuilder(getEntity());
                Block block = location.clone().subtract(0, -1, 0).getBlock();
//            if (npc != null && block.getBlockData() instanceof Slab && ((Slab) block.getBlockData()).getType().equals(Slab.Type.BOTTOM))
//                npc.teleport(location.clone().add(0, -0.5, 0));
                if (getEntity() instanceof LivingEntity)
                    NBTBuilder.setInvisible(invisible);
                break;
        }
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
