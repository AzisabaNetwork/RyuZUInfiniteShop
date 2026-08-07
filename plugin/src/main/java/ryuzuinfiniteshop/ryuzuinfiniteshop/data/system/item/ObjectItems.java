package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
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
        if (object instanceof ItemStack){
            String tag = NBTUtil.getNMSStringTag((ItemStack) object, "Error");
            if(tag != null)
                return new MythicItem(tag, ((ItemStack) object).getAmount());
            if (MythicInstanceProvider.isLoaded() && Config.saveByMMID && MythicInstanceProvider.getInstance().getID((ItemStack) object) != null)
                return new MythicItem(MythicInstanceProvider.getInstance().getID((ItemStack) object), ((ItemStack) object).getAmount());
        }
        if (object instanceof MythicItem && !Config.saveByMMID)
            return ((MythicItem) object).convertItemStack();
        if (object instanceof ItemStack && ((ItemStack) object).hasItemMeta() && ((ItemStack) object).getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) ((ItemStack) object).getItemMeta();
            if (meta.hasOwner()) return object;
            GameProfile data = NBTUtil.getSkullData(meta);
            if (data == null) return object;
            PropertyMap map = data.getProperties();
            GameProfile result = new GameProfile(data.getId(), JavaUtil.getOrDefault(data.getName(), ""));
            result.getProperties().putAll(map);
            NBTUtil.setSkullData(meta, result);
            ((ItemStack) object).setItemMeta(meta);
            return object;
        } else
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
