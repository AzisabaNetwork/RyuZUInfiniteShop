package ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.PageNavigationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.CitizensHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EquipmentUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.XMaterial;

import java.util.HashMap;
import java.util.List;

//ショップエディターのメインページ
@Getter
public class ShopEditorGui extends ShopGui {
    public enum ShopSettings {
        Age,
        Sitting,
        Power,
        Profession, Biome, Level,
        Visible,
        ParrotColor,
        CatType,
        AxolotlType,
        Derp,
        RabbitType,
        DyeColor,
        SizeIncrease,
        SizeDecrease,
        BodyColor,
        PatternColor,
        Pattern,
        OptionalInfo,
        HorseColor,
        HorseStyle
    }

    private final HashMap<Integer, ShopSettings> SettingsMap = new HashMap<>();

    public ShopEditorGui(Shop shop, int page) {
        super(shop, page);
    }

    @Override
    public Inventory getInventory(ShopMode mode) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(mode, getShop(), this), 9 * 6, PageNavigationUtil.title(getShop().getDisplayNameOrElseShop(), getPage(), getShop().getEditorPageCountFromTradesCount()));

        //アイテムを設置
        setDisplay(inv);

        return inv;
    }

    //エディターに装備を置く
    private void setEquipment(Inventory inv) {
        if (getShop().getEntity() instanceof LivingEntity) {
            for (Integer slot : EquipmentUtil.getEquipmentsSlot().keySet()) {
                inv.setItem(slot, getShop().getEquipmentDisplayItem(EquipmentUtil.getEquipmentSlot(slot)));
            }
        }
    }

    private void setTradesPage(Inventory inv) {
        int lastslot = getTradeLastSlotNumber();
        for (int i = 0; i <= lastslot; i++) {
            inv.setItem(i, ItemUtil.getNamedItem(ItemUtil.getColoredItem("LIME_STAINED_GLASS_PANE"), ChatColor.GREEN + LanguageKey.INVENTORY_PAGE.getMessage(getTradePageNumber(i))));
        }
        int newslot = getTradeNewSlotNumber();
        if (newslot == 18)
            getShop().createEditorNewPage();
        else if (newslot != -1)
            inv.setItem(newslot, ItemUtil.getNamedItem(ItemUtil.getColoredItem("WHITE_STAINED_GLASS_PANE"), ChatColor.YELLOW + LanguageKey.ITEM_EDITOR_NEW_PAGE.getMessage()));
    }

    private void setTeleport(Inventory inv) {
        inv.setItem(2 * 9 + 8, ItemUtil.getNamedItem(Material.COMPASS, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_TELEPORT_TO_NPC.getMessage()));
    }

    private void setShopStatus(Inventory inv) {
        if (MythicInstanceProvider.isLoaded())
            inv.setItem(4 * 9 + 3, ItemUtil.getNamedItem(XMaterial.matchXMaterial("ENDER_EYE").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SET_MYTHICMOBID.getMessage()));
        if (CitizensHandler.isLoaded())
            inv.setItem(2 * 9 + 7, ItemUtil.getNamedItem(XMaterial.matchXMaterial("TOTEM_OF_UNDYING").get().parseMaterial(), ChatColor.GREEN + "CitizensのNpcを登録する"));
        inv.setItem(4 * 9 + 4, ItemUtil.getNamedItem(Material.ENDER_PEARL, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SET_ENTITYTYPE.getMessage()));
        inv.setItem(4 * 9 + 5, getShop().isSearchable() ?
                ItemUtil.getNamedItem(XMaterial.matchXMaterial("CLOCK").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_SEARCH_SELECT.getMessage()) :
                ItemUtil.getNamedEnchantedItem(XMaterial.matchXMaterial("CLOCK").get().parseMaterial(), ChatColor.RED + LanguageKey.ITEM_SEARCH_NOT_SELECTABLE.getMessage())
        );
        inv.setItem(4 * 9 + 6, getShop().isLock() ?
                ItemUtil.getNamedEnchantedItem(Material.TRIPWIRE_HOOK, ChatColor.RED + LanguageKey.ITEM_EDITOR_LOCKED.getMessage()) :
                ItemUtil.getNamedItem(Material.TRIPWIRE_HOOK, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_UNLOCKED.getMessage())
        );
        inv.setItem(4 * 9 + 7, ItemUtil.getNamedItem(Material.ARROW, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_CHANGE_DIRECTION.getMessage()));
        inv.setItem(4 * 9 + 8, ItemUtil.getNamedItem(Material.MAGENTA_GLAZED_TERRACOTTA, getShop().getShopType().getShopTypeDisplay()));
    }

    private void setShopOperation(Inventory inv) {
        inv.setItem(5 * 9 + 4, ItemUtil.getNamedItem(Material.NAME_TAG, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SET_NAME.getMessage(), ChatColor.YELLOW + LanguageKey.ITEM_CURRENT_NAME.getMessage(getShop().getDisplayNameOrElseNone())));
        inv.setItem(5 * 9 + 5, ItemUtil.getNamedItem(Material.BARRIER, ChatColor.RED + LanguageKey.ITEM_EDITOR_SHOP_DELETE.getMessage()));
        inv.setItem(5 * 9 + 6, ItemUtil.getNamedItem(Material.NETHER_STAR, ChatColor.YELLOW + LanguageKey.ITEM_EDITOR_UPDATE_SHOP.getMessage()));
        inv.setItem(5 * 9 + 7, ItemUtil.getNamedItem(Material.DIAMOND, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_CONVERT_SHOP_TO_ITEMS.getMessage()));
        inv.setItem(5 * 9 + 8, ItemUtil.getNamedEnchantedItem(ShopTrade.getFilter(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_LOAD_TRADES.getMessage()));
    }

    public void setDisplay(Inventory inv) {
        setTradesPage(inv);
        setShopStatus(inv);
        setShopOperation(inv);
        setTeleport(inv);
        if (!(MythicInstanceProvider.isLoaded() && getShop().getMythicmob() != null)) {
            setEquipment(inv);
            if (!getShop().getNpcType().equals(NpcType.CITIZEN)) {
                setSettings(inv);
            }
        }
        setCover(inv);
    }

    public void setCover(Inventory inv) {
        ItemStack item = ItemUtil.getWhitePanel();
        for (int i = 0; i < 2 * 9; i++)
            if (ItemUtil.isAir(inv.getItem(i))) inv.setItem(i, ShopTrade.getFilter());
        for (int i = 2 * 9; i < 6 * 9; i++)
            if (ItemUtil.isAir(inv.getItem(i))) inv.setItem(i, item);
    }

    public void setSettings(Inventory inv) {
        SettingsMap.clear();
        setVisible(inv);
        setAge(inv);
        setSitting(inv);
        setPower(inv);
        setProfession(inv);
        setParrotColor(inv);
        setCatType(inv);
        setRabbitType(inv);
        setSizeIncrease(inv);
        setSizeDecrease(inv);
        setDyeColor(inv);
        setOptinalInfo(inv);
        setHorseColor(inv);
        setHorseStyle(inv);
        setBodyColor(inv);
        setPatternColor(inv);
        setPattern(inv);
        setAxolotlType(inv);
        setDerp(inv);
        if (RyuZUInfiniteShop.VERSION < 14) return;
        setBiome(inv);
        setLevel(inv);
    }

    private void setAge(Inventory inv) {
        if (!(getShop() instanceof AgeableShop)) return;
        ItemStack item = ((AgeableShop) getShop()).isAdult() ?
                ItemUtil.getNamedItem(Material.STONE, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_AGE_ADULT.getMessage()) :
                ItemUtil.getNamedItem(Material.STONE_BUTTON, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_AGE_CHILD.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Age);
    }

    private void setSitting(Inventory inv) {
        if (!(getShop() instanceof SittableShop)) return;
        ItemStack item = ((SittableShop) getShop()).isSitting() ?
                ItemUtil.getNamedItem(XMaterial.matchXMaterial("STONE_PRESSURE_PLATE").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SITTING.getMessage()) :
                ItemUtil.getNamedItem(Material.STONE, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_STANDING.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Sitting);
    }

    private void setPower(Inventory inv) {
        if (!(getShop() instanceof PoweredableShop)) return;
        ItemStack item = ((PoweredableShop) getShop()).isPowered() ?
                ItemUtil.getNamedEnchantedItem(XMaterial.matchXMaterial("CREEPER_SPAWN_EGG").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_POWERED.getMessage()) :
                ItemUtil.getNamedItem(XMaterial.matchXMaterial("CREEPER_SPAWN_EGG").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_NOT_POWERED.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Power);
    }

    private void setDerp(Inventory inv) {
        if (!(getShop() instanceof SnowmanShop)) return;
        ItemStack item = ((SnowmanShop) getShop()).isDerp() ?
                ItemUtil.getNamedEnchantedItem(Material.SHEARS, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_POWERED.getMessage()) :
                ItemUtil.getNamedItem(Material.SHEARS, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_NOT_POWERED.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Derp);
    }

    private void setSizeIncrease(Inventory inv) {
        if (!(getShop() instanceof SlimeShop)) return;
        ItemStack item = ItemUtil.getNamedItem(ItemUtil.getBooleanItem(true), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SIZE_INCREASE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.SizeIncrease);
    }

    private void setSizeDecrease(Inventory inv) {
        if (!(getShop() instanceof SlimeShop)) return;
        ItemStack item = ItemUtil.getNamedItem(ItemUtil.getBooleanItem(false), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_SIZE_DECREASE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.SizeDecrease);
    }

    private void setBodyColor(Inventory inv) {
        if (!(getShop() instanceof TropicalFishShop)) return;
        ItemStack item = ItemUtil.getNamedItem(ItemUtil.getColoredItem(((TropicalFishShop) getShop()).getBodyColor()), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_BODY_COLOR.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.BodyColor);
    }

    private void setPatternColor(Inventory inv) {
        if (!(getShop() instanceof TropicalFishShop)) return;
        ItemStack item = ItemUtil.getNamedItem(ItemUtil.getColoredItem(((TropicalFishShop) getShop()).getBodyColor()), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_PATTERN_COLOR.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.PatternColor);
    }

    private void setPattern(Inventory inv) {
        if (!(getShop() instanceof TropicalFishShop)) return;
        ItemStack item = ItemUtil.getNamedItem(Material.GREEN_GLAZED_TERRACOTTA, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_PATTERN.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Pattern);
    }

    private void setParrotColor(Inventory inv) {
        if (!(getShop() instanceof ParrotShop)) return;
        ItemStack item = ItemUtil.getNamedItem(((ParrotShop) getShop()).getColorItem(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_PARROT_COLOR.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.ParrotColor);
    }

    private void setCatType(Inventory inv) {
        if (!(getShop() instanceof CatShop)) return;
        ItemStack item = ItemUtil.getNamedItem(Material.BLACK_GLAZED_TERRACOTTA, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_CAT_TYPE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.CatType);
    }

    private void setAxolotlType(Inventory inv) {
        if (!(getShop() instanceof AxolotlShop)) return;
        ItemStack item = ItemUtil.getNamedItem(Material.BLACK_GLAZED_TERRACOTTA, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_CAT_TYPE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.AxolotlType);
    }

    private void setRabbitType(Inventory inv) {
        if (!(getShop() instanceof RabbitShop)) return;
        ItemStack item = ItemUtil.getNamedItem(Material.BLACK_GLAZED_TERRACOTTA, ChatColor.GREEN + LanguageKey.ITEM_EDITOR_RABBIT_TYPE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.RabbitType);
    }

    private void setDyeColor(Inventory inv) {
        if (!(getShop() instanceof DyeableShop)) return;
        ItemStack item = ItemUtil.getNamedItem(((DyeableShop) getShop()).getColorMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_DYE_COLOR.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.DyeColor);
    }

    private void setOptinalInfo(Inventory inv) {
        if (!(getShop() instanceof DyeableShop)) return;
        ItemStack item = ItemUtil.getNamedItem(ItemUtil.getBooleanItem(((DyeableShop) getShop()).getOptionalInfo()), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_OPTIONAL_INFO.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.OptionalInfo);
    }

    private void setHorseColor(Inventory inv) {
        if (!(getShop() instanceof HorseShop)) return;
        ItemStack item = ItemUtil.getNamedItem(((HorseShop) getShop()).getColorMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_HORSE_COLOR.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.HorseColor);
    }

    private void setHorseStyle(Inventory inv) {
        if (!(getShop() instanceof HorseShop)) return;
        ItemStack item = ItemUtil.getNamedItem(XMaterial.matchXMaterial("GRAY_GLAZED_TERRACOTTA").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_EDITOR_HORSE_STYLE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.HorseStyle);
    }

    private void setProfession(Inventory inv) {
        if (!(getShop() instanceof VillagerableShop)) return;
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, ItemUtil.getNamedItem(((VillagerableShop) getShop()).getJobBlockMaterial(), ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_JOB_CHANGE.getMessage()));
        SettingsMap.put(slot, ShopSettings.Profession);
    }

    private void setBiome(Inventory inv) {
        if (!(getShop() instanceof VillagerableShop)) return;
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, ItemUtil.getNamedItem(((VillagerableShop) getShop()).getBiomeMaterial(), ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_BIOME_CHANGE.getMessage()));
        SettingsMap.put(slot, ShopSettings.Biome);
    }

    private void setLevel(Inventory inv) {
        if (!(getShop() instanceof VillagerableShop)) return;
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, ItemUtil.getNamedItem(((VillagerableShop) getShop()).getLevelMaterial(), ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_LEVEL_CHANGE.getMessage()));
        SettingsMap.put(slot, ShopSettings.Level);
    }


    private void setVisible(Inventory inv) {
        if (!(getShop().getEntity() instanceof LivingEntity)) return;
        ItemStack item = getShop().isInvisible() ?
                ItemUtil.getNamedItem(Material.GLASS, ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_VISIBILITY_INVISIBLE.getMessage()) :
                ItemUtil.getNamedItem(XMaterial.matchXMaterial("POLISHED_ANDESITE").get().parseMaterial(), ChatColor.GREEN + LanguageKey.ITEM_SETTINGS_VISIBILITY_VISIBLE.getMessage());
        int slot = 3 * 9 + 8 - SettingsMap.size();
        inv.setItem(slot, item);
        SettingsMap.put(slot, ShopSettings.Visible);
    }

    public int getTradePageByRawNumber(int slot) {
        return slot + 1 + (getPage() - 1) * 18;
    }

    public int getTradePageNumber(int slot) {
        int page = getTradePageByRawNumber(slot);
        return page <= getShop().getPageCount() ? page : 0;
    }

    public int getTradeLastSlotNumber() {
        return Math.min(17, getShop().getPageCount() - 1 - (getPage() - 1) * 18);
    }

    public int getTradeNewSlotNumber() {
        int last = getTradeLastSlotNumber();
        if (last == -1) return 0;
        if (getShop().isLimitPage(getTradePageNumber(last)))
            return last + 1;
        else
            return -1;
    }

}
