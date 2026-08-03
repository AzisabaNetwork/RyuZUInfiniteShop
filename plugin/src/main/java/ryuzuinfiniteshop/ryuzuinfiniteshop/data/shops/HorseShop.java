package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;

import java.util.Arrays;
import java.util.function.Consumer;

public class HorseShop extends AgeableShop {
    protected Horse.Color color;
    protected Horse.Style style;

    public HorseShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setStyle(Horse.Style style) {
        this.style = style;
        Entity npc = getEntity();
        if (npc == null) return;
        ((Horse) npc).setStyle(style);
    }

    public void setColor(Horse.Color color) {
        this.color = color;
        Entity npc = getEntity();
        if (npc == null) return;
        ((Horse) npc).setColor(color);
//        NBTBuilder.setVariant(color.name().toLowerCase(), style.name().toLowerCase());
    }

    public Horse.Style getNextStyle() {
        int nextindex = Arrays.asList(Horse.Style.values()).indexOf(style) + 1;
        return nextindex == Horse.Style.values().length ?
                Horse.Style.values()[0] :
                Horse.Style.values()[nextindex];
    }

    public Horse.Color getNextColor() {
        int nextindex = Arrays.asList(Horse.Color.values()).indexOf(color) + 1;
        return nextindex == Horse.Color.values().length ?
                Horse.Color.values()[0] :
                Horse.Color.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> {
            yaml.set("Npc.Options.Color", color.name());
            yaml.set("Npc.Options.Style", style.name());
        });
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            String rawColor = yaml.getString("Npc.Options.Color", "WHITE");
            try {
                this.color = Horse.Color.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(rawColor, "WHITE"));
            } catch (Exception e) {
                this.color = Horse.Color.WHITE;
            }

            String rawStyle = yaml.getString("Npc.Options.Style", "NONE");
            try {
                this.style = Horse.Style.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(rawStyle, "NONE"));
            } catch (Exception e) {
                this.style = Horse.Style.NONE;
            }
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) {
            setStyle(style);
            setColor(color);
        }
    }

    public Material getColorMaterial() {
        switch (color) {
            case CREAMY:
                return Material.WHITE_GLAZED_TERRACOTTA;
            case CHESTNUT:
                return Material.BROWN_GLAZED_TERRACOTTA;
            case DARK_BROWN:
                return Material.BLACK_GLAZED_TERRACOTTA;
            case GRAY:
                return Material.getMaterial("GRAY_WOOL");
            case BROWN:
                return Material.getMaterial("BROWN_WOOL");
            case WHITE:
            default:
                return Material.getMaterial("WHITE_WOOL");
        }

    }
}
