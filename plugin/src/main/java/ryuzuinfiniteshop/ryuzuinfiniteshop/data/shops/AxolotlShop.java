package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.function.Consumer;

public class AxolotlShop extends Shop {
    protected Axolotl.Variant type;

    public AxolotlShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setCatType(Axolotl.Variant type) {
        this.type = type;
        Entity npc = getEntity();
        if (npc == null) return;
        ((Axolotl) npc).setVariant(type);
    }

    public Axolotl.Variant getNextType() {
        int nextindex = Arrays.asList(Axolotl.Variant.values()).indexOf(type) + 1;
        return nextindex == Axolotl.Variant.values().length ?
                Axolotl.Variant.values()[0] :
                Axolotl.Variant.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> yaml.set("Npc.Options.Color", type.name()));
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            String raw = yaml.getString("Npc.Options.Color", "LUCY");
            try {
                this.type = Axolotl.Variant.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(raw, "LUCY"));
            } catch (Exception e) {
                this.type = Axolotl.Variant.LUCY;
            }
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) setCatType(type);
    }
}
