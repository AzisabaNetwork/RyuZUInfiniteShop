package ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.SpawnShopData;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.LocationUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

public class EntityUtil {
    public static final Queue<SpawnShopData> entityQueue = new ArrayDeque<>();

    public static DyeColor getNextColor(DyeColor color) {
        int nextindex = Arrays.asList(DyeColor.values()).indexOf(color) + 1;
        return nextindex == DyeColor.values().length ?
                DyeColor.values()[0] :
                DyeColor.values()[nextindex];
    }

    public static ArmorStand spawnHologram(Location location, String text) {
        if (JavaUtil.isEmptyString(text)) return null;
        ArmorStand hologram = (ArmorStand) EntityUtil.spawnEntity(LocationUtil.getMiddleLocation(location), EntityType.ARMOR_STAND);
        hologram = (ArmorStand) NBTUtil.setNMSTag(hologram, "Shop", "Hologram");
        new EntityNBTBuilder(hologram).setInvisible(true);
        hologram.setCustomName(text);
        hologram.setCustomNameVisible(true);
        hologram.setInvulnerable(true);
        hologram.setGravity(false);
        hologram.setAI(false);
        hologram.setMarker(true);
        hologram.setVisible(false);
        hologram.setRemoveWhenFarAway(true);
        hologram.setSmall(true);
        return hologram;
    }

    public static Entity spawnEntity(Location location, EntityType entityType) {
        entityQueue.add(new SpawnShopData(location, entityType));
        try {
            // CraftWorldクラスをリフレクションを使って取得
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".CraftWorld");
            // EntityTypeオブジェクトを取得する
            Object entityTypeObj = entityType.getClass().getField(entityType.name()).get(null);
            // CraftWorld#spawnEntity(Location, EntityType, CreatureSpawnEvent.SpawnReason)メソッドを取得
            Method spawnEntityMethod = craftWorldClass.getMethod("spawnEntity", Location.class, entityType.getClass(), CreatureSpawnEvent.SpawnReason.class);
            // メソッドを実行し、エンティティを召喚する
            Object entityObject = spawnEntityMethod.invoke(craftWorldClass.cast(location.getWorld()), location, entityTypeObj, CreatureSpawnEvent.SpawnReason.CUSTOM);
            UUID uuid = (UUID) entityObject.getClass().getMethod("getUniqueId").invoke(entityObject);
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) return location.getWorld().spawnEntity(location, entityType);
            return entity;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException | InvocationTargetException |
                 NoSuchMethodException e) {
            return location.getWorld().spawnEntity(location, entityType);
        }
    }

    private static String getServerVersion() {
        String packageName = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}
