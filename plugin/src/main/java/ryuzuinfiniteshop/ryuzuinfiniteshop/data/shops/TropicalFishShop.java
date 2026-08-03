package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import lombok.Getter;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TropicalFish;

import java.util.Arrays;
import java.util.function.Consumer;

@Getter
public class TropicalFishShop extends Shop {
    protected DyeColor bodyColor;
    protected DyeColor patternColor;
    protected TropicalFish.Pattern pattern;

    public TropicalFishShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setBodyColor(DyeColor bodyColor) {
        this.bodyColor = bodyColor;
        Entity npc = getEntity();
        if (npc == null) return;
        ((TropicalFish) npc).setBodyColor(bodyColor);
    }

    public void setPatternColor(DyeColor patternColor) {
        this.patternColor = patternColor;
        Entity npc = getEntity();
        if (npc == null) return;
        ((TropicalFish) npc).setBodyColor(patternColor);
    }

    public void setPattern(TropicalFish.Pattern pattern) {
        this.pattern = pattern;
        Entity npc = getEntity();
        if (npc == null) return;
        ((TropicalFish) npc).setPattern(pattern);
    }

    public TropicalFish.Pattern getNextPattern() {
        int nextindex = Arrays.asList(TropicalFish.Pattern.values()).indexOf(pattern) + 1;
        return nextindex == TropicalFish.Pattern.values().length ?
                TropicalFish.Pattern.values()[0] :
                TropicalFish.Pattern.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> {
            yaml.set("Npc.Options.BodyColor", bodyColor.name());
            yaml.set("Npc.Options.PatternColor", patternColor.name());
            yaml.set("Npc.Options.Pattern", pattern.name());
        });
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            try {
                this.bodyColor = DyeColor.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(yaml.getString("Npc.Options.BodyColor", "RED"), "RED"));
            } catch (Exception e) {
                this.bodyColor = DyeColor.RED;
            }
            try {
                this.patternColor = DyeColor.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(yaml.getString("Npc.Options.PatternColor", "RED"), "RED"));
            } catch (Exception e) {
                this.patternColor = DyeColor.RED;
            }
            try {
                this.pattern = TropicalFish.Pattern.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(yaml.getString("Npc.Options.Pattern", "KOB"), "KOB"));
            } catch (Exception e) {
                this.pattern = TropicalFish.Pattern.KOB;
            }
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) {
            setBodyColor(bodyColor);
            setPatternColor(patternColor);
            setPattern(pattern);
        }
    }
}
