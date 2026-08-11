package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Slime;
import org.bukkit.material.Colorable;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.*;

/**
 * Factory for creating Shop instances based on entity type.
 */
public final class ShopFactory {

    private ShopFactory() {}

    /**
     * Creates the appropriate Shop subclass for the given entity type.
     */
    public static Shop createShop(Location location, String type, ConfigurationSection config) {
        if (type.equalsIgnoreCase("BLOCK"))
            return new Shop(location, type, config);

        EntityType entityType = EntityType.valueOf(type);

        if (entityType.equals(EntityType.VILLAGER) || entityType.equals(EntityType.ZOMBIE_VILLAGER))
            return new VillagerableShop(location, type, config);
        if (entityType.equals(EntityType.CREEPER))
            return new PoweredableShop(location, type, config);
        if (Slime.class.isAssignableFrom(entityType.getEntityClass()))
            return new SlimeShop(location, type, config);
        if (Colorable.class.isAssignableFrom(entityType.getEntityClass()) || entityType.equals(EntityType.WOLF))
            return new DyeableShop(location, type, config);
        if (entityType.equals(EntityType.PARROT))
            return new ParrotShop(location, type, config);
        if (entityType.equals(EntityType.CAT))
            return new CatShop(location, type, config);
        if (entityType.equals(EntityType.AXOLOTL))
            return new AxolotlShop(location, type, config);
        if (entityType.equals(EntityType.SNOW_GOLEM))
            return new SnowmanShop(location, type, config);
        if (entityType.equals(EntityType.RABBIT))
            return new RabbitShop(location, type, config);
        if (entityType.equals(EntityType.HORSE))
            return new HorseShop(location, type, config);
        if (Ageable.class.isAssignableFrom(entityType.getEntityClass()))
            return new AgeableShop(location, type, config);
        if (entityType.equals(EntityType.TROPICAL_FISH))
            return new TropicalFishShop(location, type, config);

        return new Shop(location, type, config);
    }
}
