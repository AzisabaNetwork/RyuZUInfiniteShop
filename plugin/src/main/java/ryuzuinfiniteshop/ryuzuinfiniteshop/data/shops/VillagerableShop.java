package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.XMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.bukkit.entity.Villager.Profession.*;
import static org.bukkit.entity.Villager.Type.*;

@Getter
public class VillagerableShop extends AgeableShop {
    protected Villager.Profession profession;
    protected Villager.Type biome;
    protected int level = 1;
    private static final Random random = new Random();

    public VillagerableShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setProfession(Villager.Profession profession) {
        this.profession = profession;
        Entity npc = getEntity();
        if (npc == null) return;
        if (npc instanceof Villager) {
            if(RyuZUInfiniteShop.VERSION < 14){
                ((Villager) npc).setProfession(Villager.Profession.valueOf(profession.name().equals("NORMAL") || profession.name().equals("HUSK") ? "FARMER" : profession.name()));
//                setVillagerCareer((Villager) npc, profession.name());
            }
            else
                ((Villager) npc).setProfession(profession);
            ((Villager) npc).setRecipes(new ArrayList<>());
        }
        else
            ((ZombieVillager) npc).setVillagerProfession(profession);
//        NBTBuilder.setVillagerData(profession, biome, level);
    }

    public void setBiome(Villager.Type villagertype) {
        this.biome = villagertype;
        Entity npc = getEntity();
        if (npc == null) return;
        if (npc instanceof Villager)
            ((Villager) npc).setVillagerType(villagertype);
        else
            ((ZombieVillager) npc).setVillagerType(villagertype);
//        NBTBuilder.setVillagerData(profession, biome, level);
    }

    public void setLevel(int level) {
        this.level = level;
        Entity npc = getEntity();
        if (npc == null) return;
        if (npc instanceof Villager)
            ((Villager) npc).setVillagerLevel(level);
//        NBTBuilder.setVillagerData(profession, biome, level);
    }

    public Villager.Profession getNextProfession() {
        List<Villager.Profession> professions = Arrays.asList(Villager.Profession.values()).stream().filter(profession -> !profession.name().equals("NORMAL") && !profession.name().equals("HUSK")).collect(Collectors.toList());;
        int nextindex = professions.indexOf(profession) + 1;
        return nextindex == professions.size() ?
                professions.get(0) :
                professions.get(nextindex);
    }

    public Villager.Type getNextBiome() {
        int nextindex = Arrays.asList(Villager.Type.values()).indexOf(biome) + 1;
        return nextindex == Villager.Type.values().length ?
                Villager.Type.values()[0] :
                Villager.Type.values()[nextindex];
    }

    @Override
    public Consumer<YamlConfiguration> getSaveYamlProcess() {
        return super.getSaveYamlProcess().andThen(yaml -> {
            yaml.set("Npc.Options.Profession", JavaUtil.getOrDefault(profession, Villager.Profession.FARMER).toString());
            if (RyuZUInfiniteShop.VERSION < 14) return;
            yaml.set("Npc.Options.Biome", JavaUtil.getOrDefault(biome, PLAINS).toString());
            yaml.set("Npc.Options.Level", level);
        });
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            try {
                this.profession = Villager.Profession.valueOf(yaml.getString("Npc.Options.Profession", RyuZUInfiniteShop.VERSION >= 14 ? "NONE" : "NORMAL"));
            } catch (IllegalArgumentException e) {
                this.profession = Villager.Profession.FARMER;
            }
            if (RyuZUInfiniteShop.VERSION < 14) return;
            try {
                this.biome = Villager.Type.valueOf(yaml.getString("Npc.Options.Biome", "PLAINS"));
            } catch (RuntimeException e) {
                this.biome = PLAINS;
            }
            this.level = yaml.getInt("Npc.Options.Level", 1);
        });
    }

    @Override
    public void respawnNPC() {
        super.respawnNPC();
        if (isEditableNpc()) {
            setProfession(profession);
            if (RyuZUInfiniteShop.VERSION < 14) return;
            setBiome(biome);
            setLevel(level);
        }
    }

    public Material getJobBlockMaterial() {
        if (RyuZUInfiniteShop.VERSION >= 14) {
            if (profession.equals(NITWIT)) {
                return Material.GREEN_STAINED_GLASS;
            } else if (profession.equals(ARMORER)) {
                return Material.BLAST_FURNACE;
            } else if (profession.equals(BUTCHER)) {
                return Material.SMOKER;
            } else if (profession.equals(CARTOGRAPHER)) {
                return Material.CARTOGRAPHY_TABLE;
            } else if (profession.equals(CLERIC)) {
                return Material.BREWING_STAND;
            } else if (profession.equals(FARMER)) {
                return Material.COMPOSTER;
            } else if (profession.equals(FISHERMAN)) {
                return Material.BARREL;
            } else if (profession.equals(FLETCHER)) {
                return Material.FLETCHING_TABLE;
            } else if (profession.equals(LEATHERWORKER)) {
                return Material.CAULDRON;
            } else if (profession.equals(LIBRARIAN)) {
                return Material.LECTERN;
            } else if (profession.equals(MASON)) {
                return Material.STONECUTTER;
            } else if (profession.equals(SHEPHERD)) {
                return Material.LOOM;
            } else if (profession.equals(TOOLSMITH)) {
                return Material.SMITHING_TABLE;
            } else if (profession.equals(WEAPONSMITH)) {
                return Material.GRINDSTONE;
            }
            return Material.WHITE_STAINED_GLASS;
        } else {
            return switch (profession.name()) {
                case "NORMAL" -> Material.DIRT;
                case "FARMER" -> Material.WHEAT;
                case "LIBRARIAN" -> Material.BOOKSHELF;
                case "PRIEST" -> Material.ROTTEN_FLESH;
                case "BLACKSMITH" -> Material.ANVIL;
                case "BUTCHER" -> XMaterial.matchXMaterial("PORKCHOP").get().parseMaterial();
                case "NITWIT" -> Material.SLIME_BALL;
                default -> Material.STONE;
            };
        }
    }

    public Material getBiomeMaterial() {
        if (biome.equals(DESERT)) {
            return Material.SAND;
        } else if (biome.equals(JUNGLE)) {
            return Material.JUNGLE_LOG;
        } else if (biome.equals(SAVANNA)) {
            return Material.TERRACOTTA;
        } else if (biome.equals(SNOW)) {
            return Material.SNOW_BLOCK;
        } else if (biome.equals(SWAMP)) {
            return Material.LILY_PAD;
        } else if (biome.equals(TAIGA)) {
            return Material.DARK_OAK_LOG;
        }
        return Material.GRASS_BLOCK;
    }

    public Material getLevelMaterial() {
        switch (level) {
            case 2 -> {
                return Material.IRON_INGOT;
            }
            case 3 -> {
                return Material.GOLD_INGOT;
            }
            case 4 -> {
                return Material.EMERALD;
            }
            case 5 -> {
                return Material.DIAMOND;
            }
            default -> {
                return Material.COBBLESTONE;
            }
        }
    }
}
