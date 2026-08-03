package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;

import java.util.Arrays;
import java.util.function.Consumer;

public class ParrotShop extends SittableShop {
    protected Parrot.Variant color;

    public ParrotShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setColor(Parrot.Variant color) {
        this.color = color;
        Entity npc = getEntity();
        if (npc == null) return;
        ((Parrot) npc).setVariant(color);
//        NBTBuilder.setVariant(color.ordinal());
    }

    public Parrot.Variant getNextColor() {
        int nextindex = Arrays.asList(Parrot.Variant.values()).indexOf(color) + 1;
        return nextindex == Parrot.Variant.values().length ?
                Parrot.Variant.values()[0] :
                Parrot.Variant.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> yaml.set("Npc.Options.Color", color.name()));
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            this.color = Parrot.Variant.valueOf(yaml.getString("Npc.Options.Color", "RED"));
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) setColor(color);
    }

    public ItemStack getColorItem() {
        return ItemUtil.getColoredItem(color.name() + "_WOOL");
    }
}
