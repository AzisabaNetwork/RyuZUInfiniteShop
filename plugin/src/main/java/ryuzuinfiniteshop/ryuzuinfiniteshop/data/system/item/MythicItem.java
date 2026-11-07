package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item;

import lombok.Value;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LegacyMythicItemMatcher;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
public class MythicItem implements ConfigurationSerializable {
    String id;
    int amount;

    public ItemStack convertItemStack() {
        if (!MythicInstanceProvider.isLoaded())
            return ItemUtil.clone(NBTUtil.setNMSTag(ItemUtil.getNamedItem(Material.REDSTONE_BLOCK, ChatColor.RED + LanguageKey.ERROR_INVALID_LOADED_MYTHICMOBS.getMessage(id)), "Error", id), amount);
        ItemStack item = MythicInstanceProvider.getInstance().getMythicItem(id, amount);
        if (item == null)
            return ItemUtil.clone(NBTUtil.setNMSTag(ItemUtil.getNamedItem(Material.REDSTONE_BLOCK, ChatColor.RED + LanguageKey.ERROR_MYTHICMOBS_INVALID_ID.getMessage(id)), "Error", id), amount);
        return item;
    }

    /**
     * Decides how a stack put into (or loaded into) a shop is kept.
     *
     * @return a {@link MythicItem} or a regenerated MythicMobs {@link ItemStack} replacing {@code item},
     * or null to keep {@code item} as it is
     */
    static Object resolve(ItemStack item) {
        String error = NBTUtil.getNMSStringTag(item, "Error");
        if (error != null) return new MythicItem(error, item.getAmount());
        if (!MythicInstanceProvider.isLoaded()) return null;
        String id = MythicInstanceProvider.getInstance().getID(item);
        if (id != null) return Config.saveByMMID ? new MythicItem(id, item.getAmount()) : null;
        // 1.15.2 items that lost every MythicMobs tag: identify them from the legacy item definitions.
        String legacyId = LegacyMythicItemMatcher.findId(item);
        if (legacyId == null) return null;
        MythicItem mythicItem = new MythicItem(legacyId, item.getAmount());
        return Config.saveByMMID ? mythicItem : mythicItem.convertItemStack();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("amount", amount);
        return result;
    }

    public static MythicItem deserialize(Map<String, Object> map) {
        return new MythicItem(map.get("id").toString(), Integer.parseInt(map.get("amount").toString()));
    }
}
