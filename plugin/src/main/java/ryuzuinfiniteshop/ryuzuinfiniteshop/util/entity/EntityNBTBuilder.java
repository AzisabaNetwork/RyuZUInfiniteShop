package ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public class EntityNBTBuilder {
    private final Entity entity;

    public EntityNBTBuilder(Entity entity) {
        this.entity = entity;
    }

    public void setInvisible(boolean invisible) {
//        compound.setByte("Invisible", invisible ? (byte) 1 : (byte) 0);
        ((LivingEntity) entity).setInvisible(invisible);
    }
}
