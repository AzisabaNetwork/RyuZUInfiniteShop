package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item;

import lombok.EqualsAndHashCode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemNormalizer;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(doNotUseGetters = true)
public class ObjectItems {
    private final List<Object> objects;

    public ObjectItems(Object object) {
        this.objects = ((List<Object>) object).stream().map(ObjectItems::convert).collect(Collectors.toList());
    }

    public ObjectItems(List<Object> objects) {
        this.objects = objects.stream().map(ObjectItems::convert).collect(Collectors.toList());
    }

    public ObjectItems(ItemStack[] items) {
        this.objects = Arrays.stream(items).map(ObjectItems::convert).collect(Collectors.toList());
    }

    public List<Object> getObjects() {
        return new ArrayList<>(objects);
    }

    public ItemStack[] toItemStacks() {
        return objects.stream().map(obj -> {
            if (obj instanceof MythicItem)
                return ((MythicItem) obj).convertItemStack();
            else if (obj == null || ItemUtil.isAir((ItemStack) obj))
                return new ItemStack(Material.AIR);
            else
                return (ItemStack) obj;
        }).map(ItemStack::clone).toArray(ItemStack[]::new);
    }

    public void setObject(Object object, int index) {
        objects.set(index, convert(object));
    }

    //return MythicItems or ItemStack
    //from MythicItems or ItemStack
    private static Object convert(Object object) {
        if (object instanceof ItemStack item && !ItemUtil.isAir(item)) {
            Object resolved = MythicItem.resolve(item);
            if (resolved != null) return resolved;
        }
        if (object instanceof MythicItem && !Config.saveByMMID)
            return ((MythicItem) object).convertItemStack();
        // Player heads are no longer rebuilt here: the legacy GameProfile hack breaks the profile of 1.21 heads.
        // Heads saved by 1.15.2 are repaired once by LegacySkullMigration.
        // Same data as the items players carry (AziSync, EnderChestPlus, StorageBox, ItemStash): see ItemNormalizer.
        if (object instanceof ItemStack stack) return ItemNormalizer.normalize(stack);
        return object;
    }

    public ObjectItems reconvert() {
        return new ObjectItems(toItemStacks());
    }

    @Override
    public String toString() {
        return objects.toString();
    }
}
