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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter
public class VillagerableShop extends AgeableShop {
    protected Villager.Profession profession = Villager.Profession.NONE;
    protected Villager.Type biome = Villager.Type.PLAINS;
    protected int level = 1;
    private static final Random random = new Random();

    public VillagerableShop(Location location, String entityType, ConfigurationSection config) {
        super(location, entityType, config);
    }

    public void setProfession(Villager.Profession profession) {
        this.profession = JavaUtil.getOrDefault(profession, Villager.Profession.NONE);
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
            ((ZombieVillager) npc).setVillagerProfession(this.profession);
//        NBTBuilder.setVillagerData(profession, biome, level);
    }

    public void setBiome(Villager.Type villagertype) {
        this.biome = JavaUtil.getOrDefault(villagertype, Villager.Type.PLAINS);
        Entity npc = getEntity();
        if (npc == null) return;
        if (npc instanceof Villager)
            ((Villager) npc).setVillagerType(this.biome);
        else
            ((ZombieVillager) npc).setVillagerType(this.biome);
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
            yaml.set("Npc.Options.Biome", JavaUtil.getOrDefault(biome, Villager.Type.PLAINS).toString());
            yaml.set("Npc.Options.Level", level);
        });
    }

    @Override
    public Consumer<YamlConfiguration> getLoadYamlProcess() {
        return super.getLoadYamlProcess().andThen(yaml -> {
            this.profession = Villager.Profession.valueOf(yaml.getString("Npc.Options.Profession", RyuZUInfiniteShop.VERSION >= 14 ? "NONE" : "NORMAL"));
            if (RyuZUInfiniteShop.VERSION < 14) return;
            this.biome = Villager.Type.valueOf(yaml.getString("Npc.Options.Biome", "PLAINS"));
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
            switch (profession) {
                case NITWIT:
                    return Material.GREEN_STAINED_GLASS;
                case ARMORER:
                    return Material.BLAST_FURNACE;
                case BUTCHER:
                    return Material.SMOKER;
                case CARTOGRAPHER:
                    return Material.CARTOGRAPHY_TABLE;
                case CLERIC:
                    return Material.BREWING_STAND;
                case FARMER:
                    return Material.COMPOSTER;
                case FISHERMAN:
                    return Material.BARREL;
                case FLETCHER:
                    return Material.FLETCHING_TABLE;
                case LEATHERWORKER:
                    return Material.CAULDRON;
                case LIBRARIAN:
                    return Material.LECTERN;
                case MASON:
                    return Material.STONECUTTER;
                case SHEPHERD:
                    return Material.LOOM;
                case TOOLSMITH:
                    return Material.SMITHING_TABLE;
                case WEAPONSMITH:
                    return Material.GRINDSTONE;
                case NONE:
                default:
                    return Material.WHITE_STAINED_GLASS;
            }
        } else {
            switch (profession.name()) {
                case "NORMAL":
                    return Material.DIRT;
                case "FARMER":
                    return Material.WHEAT;
                case "LIBRARIAN":
                    return Material.BOOKSHELF;
                case "PRIEST":
                    return Material.ROTTEN_FLESH;
                case "BLACKSMITH":
                    return Material.ANVIL;
                case "BUTCHER":
                    return XMaterial.matchXMaterial("PORKCHOP").get().parseMaterial();
                case "NITWIT":
                    return Material.SLIME_BALL;
                default:
                    return Material.STONE;
            }
        }
    }

    public Material getBiomeMaterial() {
        switch (biome) {
            case DESERT:
                return Material.SAND;
            case JUNGLE:
                return Material.JUNGLE_LOG;
            case SAVANNA:
                return Material.TERRACOTTA;
            case SNOW:
                return Material.SNOW_BLOCK;
            case SWAMP:
                return Material.LILY_PAD;
            case TAIGA:
                return Material.DARK_OAK_LOG;
            case PLAINS:
            default:
                return Material.GRASS_BLOCK;
        }
    }

    public Material getLevelMaterial() {
        switch (level) {
            case 2:
                return Material.IRON_INGOT;
            case 3:
                return Material.GOLD_INGOT;
            case 4:
                return Material.EMERALD;
            case 5:
                return Material.DIAMOND;
            case 1:
            default:
                return Material.COBBLESTONE;
        }
    }
}
