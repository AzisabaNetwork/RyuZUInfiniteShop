package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.ObjectItems;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityNBTBuilder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EquipmentUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.NBTUtil;

/**
 * Manages NPC entity lifecycle for Shop objects.
 */
public final class ShopNPCManager {

    private ShopNPCManager() {}

    /**
     * Removes the NPC entity from the world.
     */
    public static void removeNPC(Shop shop) {
        Entity npc = getEntity(shop);
        if (npc != null) {
            NBTUtil.removeNMSTag(npc);
            npc.remove();
        }
        Location middleLoc = LocationUtil.getMiddleLocation(shop.location);
        if (shop.location.getWorld() != null) {
            shop.location.getWorld().getNearbyEntities(middleLoc, 0.5, 0.5, 0.5).stream()
                    .filter(entity -> NBTUtil.getNMSStringTag(entity, "Shop") != null)
                    .forEach(Entity::remove);
        }
        if (shop.npcType.equals(NpcType.CITIZEN)) {
            CitizensHandler.despawnNPC(shop);
        }
        shop.uuid = null;
    }

    /**
     * Spawns or respawns the NPC based on the shop's NPC type.
     */
    public static void respawnNPC(Shop shop) {
        if (shop.npcType != NpcType.MYTHICMOB && shop.entityType == null
                && JavaUtil.isEmptyString(shop.displayName)) return;
        if (FileUtil.isSaveBlock()) return;

        Entity npc = getEntity(shop);
        if (npc != null && npc.isValid()) return;
        if (!shop.location.getWorld().isChunkLoaded(
                shop.location.getBlockX() >> 4, shop.location.getBlockZ() >> 4)) return;

        removeNPC(shop);

        switch (shop.npcType) {
            case MYTHICMOB:
                respawnMythicMob(shop);
                return;
            case CITIZEN:
                respawnCitizen(shop);
                return;
            case BLOCK:
                respawnBlock(shop);
                return;
            default:
                respawnNormal(shop);
                break;
        }
    }

    /**
     * Sets metadata on the NPC entity (NBT tags, invulnerable, etc.).
     */
    public static void setNpcMeta(Shop shop, Entity npc) {
        if (npc == null) return;
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setGravity(false);
        npc = NBTUtil.setNMSTag(npc, "Shop", shop.getID());
        initializeLivingEntity(npc);
        if ("END_CRYSTAL".equalsIgnoreCase(shop.entityType) && npc instanceof EnderCrystal) {
            ((EnderCrystal) npc).setShowingBottom(false);
        }
    }

    /**
     * Initializes a LivingEntity with AI, collision, and persistence settings.
     */
    public static void initializeLivingEntity(Entity npc) {
        if (!(npc instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) npc;
        living.setAI(false);
        living.setCollidable(false);
        living.setRemoveWhenFarAway(true);
        living.setPersistent(false);
    }

    /**
     * Toggles invisibility on the NPC.
     */
    public static void changeInvisible(Shop shop) {
        if (!(getEntity(shop) instanceof LivingEntity)) return;
        shop.NBTBuilder.setInvisible(!shop.invisible);
        shop.invisible = !shop.invisible;
    }

    /**
     * Rotates the NPC by 45 degrees.
     */
    public static void changeNPCDirection(Shop shop) {
        Entity npc = getEntity(shop);
        if (!(npc instanceof LivingEntity)) return;
        shop.location.setYaw(shop.location.getYaw() + 45);
        npc.teleport(LocationUtil.toBlockLocationFromLocation(shop.location));
    }

    /**
     * Syncs equipment from shop data to the NPC entity.
     */
    public static void updateEquipments(Shop shop) {
        if (!shop.npcType.equals(NpcType.CITIZEN)) {
            Entity npc = getEntity(shop);
            if (npc instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) npc;
                for (EquipmentSlot slot : EquipmentUtil.getEquipmentsSlot().values()) {
                    applyEquipmentSlot(living, slot, shop.equipments);
                }
            }
        } else if (CitizensHandler.isLoaded() && CitizensHandler.isCitizensNPC(shop.uuid)) {
            for (EquipmentSlot slot : EquipmentUtil.getEquipmentsSlot().values()) {
                CitizensHandler.setEquipment(shop.uuid, slot, shop.equipments.toItemStacks()[slot.ordinal()]);
            }
            CitizensHandler.respawn(shop);
        }
    }

    // ========== Internal helpers ==========

    private static Entity getEntity(Shop shop) {
        if (shop.uuid == null) return null;
        return Bukkit.getEntity(shop.uuid);
    }

    private static void respawnMythicMob(Shop shop) {
        if (!MythicInstanceProvider.isLoaded()
                || !MythicInstanceProvider.getInstance().existsMythicMob(shop.mythicmob)) return;
        Entity npc = MythicInstanceProvider.getInstance().spawnMythicMob(
                LocationUtil.getMiddleLocation(shop.location), shop.mythicmob);
        if (npc == null) return;
        shop.uuid = npc.getUniqueId();
        npc = getEntity(shop);
        setNpcMeta(shop, npc);
    }

    private static void respawnCitizen(Shop shop) {
        if (!CitizensHandler.isLoaded()) return;
        shop.uuid = CitizensHandler.createNPC(shop);
        shop.citizen = shop.uuid;
        CitizensHandler.spawnNPC(shop);
    }

    private static void respawnBlock(Shop shop) {
        if (shop.hologram != null && shop.hologram.isValid()) return;
        shop.hologram = EntityUtil.spawnHologram(
                shop.location.clone().add(0.5, 1, 0.5), shop.displayName);
    }

    private static void respawnNormal(Shop shop) {
        EntityType entityType = EntityType.valueOf(shop.entityType);
        Location spawnLoc = LocationUtil.getMiddleLocation(shop.location);
        Entity npc = EntityUtil.spawnEntity(spawnLoc, entityType);
        if (npc == null) return;
        shop.uuid = npc.getUniqueId();
        npc.teleport(LocationUtil.toBlockLocationFromLocation(shop.location));
        setNpcMeta(shop, npc);

        npc.setCustomName(shop.displayName);
        npc.getPassengers().forEach(Entity::remove);
        if (npc.getVehicle() != null) npc.getVehicle().remove();
        if (npc instanceof LivingEntity) {
            updateEquipments(shop);
        }
        shop.NBTBuilder = new EntityNBTBuilder(getEntity(shop));
        if (getEntity(shop) instanceof LivingEntity) {
            shop.NBTBuilder.setInvisible(shop.invisible);
        }
    }

    private static void applyEquipmentSlot(LivingEntity living, EquipmentSlot slot, ObjectItems equipments) {
        switch (slot) {
            case HAND:
                living.getEquipment().setItemInMainHand(equipments.toItemStacks()[slot.ordinal()]);
                break;
            case OFF_HAND:
                living.getEquipment().setItemInOffHand(equipments.toItemStacks()[slot.ordinal()]);
                break;
            case FEET:
                living.getEquipment().setBoots(equipments.toItemStacks()[slot.ordinal()]);
                break;
            case LEGS:
                living.getEquipment().setLeggings(equipments.toItemStacks()[slot.ordinal()]);
                break;
            case CHEST:
                living.getEquipment().setChestplate(equipments.toItemStacks()[slot.ordinal()]);
                break;
            case HEAD:
                living.getEquipment().setHelmet(equipments.toItemStacks()[slot.ordinal()]);
                break;
        }
    }
}
