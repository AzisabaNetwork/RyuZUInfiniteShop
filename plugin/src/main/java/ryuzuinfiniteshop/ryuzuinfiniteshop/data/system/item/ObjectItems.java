package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item;

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

public class ObjectItems {
    private final List<Object> objects;
    /** Hashing ItemStacks is costly and trades are looked up by hash all the time (options, counts). */
    private int hash;
    private boolean hashed;

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
        ItemStack[] result = new ItemStack[objects.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toItemStack(i);
        }
        return result;
    }

    public int size() {
        return objects.size();
    }

    public ItemStack toItemStack(int index) {
        Object obj = objects.get(index);
        if (obj instanceof MythicItem mythicItem)
            return mythicItem.convertItemStack(); // already a fresh copy
        if (obj == null || ItemUtil.isAir((ItemStack) obj))
            return new ItemStack(Material.AIR);
        return ((ItemStack) obj).clone();
    }

    /**
     * Whether the stored item at {@code index} is similar to {@code item}, using the same
     * comparison as {@code toItemStack(index).isSimilar(item)} without building an array.
     */
    public boolean isSimilar(int index, ItemStack item) {
        Object obj = objects.get(index);
        if (obj instanceof MythicItem) {
            return ((MythicItem) obj).convertItemStack().isSimilar(item);
        }
        if (obj == null || ItemUtil.isAir((ItemStack) obj)) {
            return ItemUtil.isAir(item);
        }
        return ((ItemStack) obj).isSimilar(item);
    }

    /** The display name of the stored item, or null when it has none. */
    public String getDisplayName(int index) {
        Object obj = objects.get(index);
        ItemStack item;
        if (obj instanceof MythicItem) {
            item = ((MythicItem) obj).convertItemStack();
        } else if (obj == null || ItemUtil.isAir((ItemStack) obj)) {
            return null;
        } else {
            item = (ItemStack) obj;
        }
        if (!item.hasItemMeta()) return null;
        return item.getItemMeta().getDisplayName();
    }

    /** Whether any item cannot be generated (unknown MMID or MythicMobs missing). */
    public boolean hasError() {
        for (Object obj : objects) {
            if (obj instanceof MythicItem mythicItem) {
                if (mythicItem.isError()) return true;
            } else if (obj instanceof ItemStack stack && !ItemUtil.isAir(stack)
                    && NBTUtil.getNMSStringTag(stack, "Error") != null) {
                return true;
            }
        }
        return false;
    }

    public void setObject(Object object, int index) {
        objects.set(index, convert(object));
        hashed = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectItems other)) return false;
        return hashCode() == other.hashCode() && objects.equals(other.objects);
    }

    @Override
    public int hashCode() {
        if (!hashed) {
            hash = objects.hashCode();
            hashed = true;
        }
        return hash;
    }

    //return MythicItems or ItemStack
    //from MythicItems or ItemStack
    private static Object convert(Object object) {
        if (object instanceof ItemStack item && !ItemUtil.isAir(item)) {
            Object resolved = MythicItem.resolve(item);
            if (resolved != null) return resolved;
        }
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
