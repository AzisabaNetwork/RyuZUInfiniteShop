package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.function.Consumer;

public class CatShop extends SittableShop {
    protected Cat.Type type;

    public CatShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setCatType(Cat.Type type) {
        this.type = type;
        Entity npc = getEntity();
        if (npc == null) return;
        ((Cat) npc).setCatType(type);
    }

    public Cat.Type getNextType() {
        int nextindex = Arrays.asList(Cat.Type.values()).indexOf(type) + 1;
        return nextindex == Cat.Type.values().length ?
                Cat.Type.values()[0] :
                Cat.Type.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> yaml.set("Npc.Options.CatType", type.name()));
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            String raw = yaml.getString("Npc.Options.CatType", "TABBY");
            try {
                this.type = Cat.Type.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(raw, "TABBY"));
            } catch (Exception e) {
                this.type = Cat.Type.TABBY;
            }
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) setCatType(type);
    }
}
