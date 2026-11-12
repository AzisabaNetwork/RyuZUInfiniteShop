package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item;

import lombok.EqualsAndHashCode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemNormalizer;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

@EqualsAndHashCode
public class ObjectItem {
    private Object object;

    public ObjectItem(ItemStack item) {
        this.object = convert(item);
    }

    public void setObject(ItemStack item) {
        this.object = convert(item);
    }

    public Object getObject() {
        return reconvert().object;
    }

    public ItemStack toItemStack() {
        if (object instanceof MythicItem)
            return ((MythicItem) object).convertItemStack();
        else if (object == null || ItemUtil.isAir((ItemStack) object))
            return new ItemStack(Material.AIR);
        else
            return ((ItemStack) object).clone();
    }

    public boolean isSimilar(ItemStack item) {
        if (object instanceof MythicItem)
            return ((MythicItem) object).getId().equals(MythicInstanceProvider.getInstance().getID(item));
        else if (object == null || ItemUtil.isAir((ItemStack) object))
            return ItemUtil.isAir(item);
        else
            return ItemNormalizer.isSimilar((ItemStack) object, item);
    }

    private static Object convert(Object object) {
        if (object instanceof ItemStack item && !ItemUtil.isAir(item)) {
            Object resolved = MythicItem.resolve(item);
            if (resolved != null) return resolved;
        }
        if (object instanceof MythicItem && !Config.saveByMMID)
            return ((MythicItem) object).convertItemStack();
        else if (object instanceof ItemStack stack)
            return ItemNormalizer.normalize(stack); // same data as the items players carry, see ItemNormalizer
        else
            return object;
    }

    public ObjectItem reconvert() {
        return new ObjectItem(toItemStack());
    }
}
