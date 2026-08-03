package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.ObjectItems;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.CitizensHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handles YAML serialization/deserialization for Shop objects.
 */
public final class ShopSerializer {

    private ShopSerializer() {}

    /**
     * Returns the YAML file path for a shop.
     */
    public static File getFile(Shop shop) {
        return new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/" + shop.getID() + ".yml");
    }

    /**
     * Saves the shop's state to its YAML file and returns the configuration.
     */
    public static YamlConfiguration save(Shop shop) {
        File file = getFile(shop);
        YamlConfiguration yaml = new YamlConfiguration();
        shop.getSaveYamlProcess().accept(yaml);
        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs())
                throw new IOException("Could not create directory " + parent);
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException(LanguageKey.ERROR_FILE_SAVING.getMessage(file.getName()), e);
        }
        return yaml;
    }

    /**
     * Loads a shop's state from its YAML file.
     */
    public static void load(Shop shop, File file) {
        YamlConfiguration config = new YamlConfiguration();
        if (!file.isFile()) {
            shop.getLoadYamlProcess().accept(config);
            return;
        }
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(LanguageKey.ERROR_FILE_LOADING.getMessage(file.getName()), e);
        }
        shop.getLoadYamlProcess().accept(config);
    }

    /**
     * Loads additional configuration from Shopkeepers config data (used during conversion).
     */
    public static void applyShopkeepersConfig(Shop shop, ConfigurationSection config) {
        if (config == null) return;
        ConfigurationSection objectSection = config.getConfigurationSection("object");
        if (objectSection == null) objectSection = config;
        applyNpcMetaFromShopkeepersConfig(shop, objectSection);
        String name = config.getString("name", "");
        shop.displayName = name.isEmpty() ? "" : ChatColor.GREEN + name;
    }

    /**
     * Returns a Consumer that populates a YamlConfiguration from a Shop's state.
     * Subclasses can chain with .andThen() for additional fields.
     */
    public static Consumer<YamlConfiguration> getSaveYamlProcess(Shop shop) {
        return yaml -> populateYaml(shop, yaml);
    }

    /**
     * Returns a Consumer that applies a YamlConfiguration to a Shop's state.
     * Subclasses can chain with .andThen() for additional fields.
     */
    public static Consumer<YamlConfiguration> getLoadYamlProcess(Shop shop) {
        return yaml -> {
            applyYaml(shop, yaml);
            shop.updateTradeContents();
        };
    }

    // ========== Internal: populate YAML from Shop ==========

    private static void populateYaml(Shop shop, YamlConfiguration yaml) {
        yaml.set("Npc.Options.MythicMob", shop.mythicmob);
        yaml.set("Npc.Options.Citizen", shop.npcType.equals(NpcType.CITIZEN) ? shop.citizen.toString() : null);
        yaml.set("Npc.Options.DisplayName", shop.displayName);
        yaml.set("Npc.Options.EntityType", shop.entityType);
        yaml.set("Npc.Options.Invisible", shop.invisible);
        yaml.set("Shop.Options.ShopType", shop.type.toString());
        yaml.set("Npc.Options.Equipments", shop.equipments.getObjects());
        yaml.set("Npc.Status.Lock", shop.lock);
        yaml.set("Npc.Status.Searchable", shop.searchable);
        yaml.set("Trades", shop.getTrades().stream().map(ShopTrade::serialize).collect(Collectors.toList()));
        yaml.set("Npc.Status.Yaw", shop.location.getYaw());
    }

    // ========== Internal: apply YAML to Shop ==========

    @SuppressWarnings("unchecked")
    private static void applyYaml(Shop shop, YamlConfiguration yaml) {
        shop.mythicmob = yaml.getString("Npc.Options.MythicMob");
        if (shop.mythicmob != null) {
            shop.npcType = NpcType.MYTHICMOB;
            if (MythicInstanceProvider.isLoaded() && !MythicInstanceProvider.getInstance().existsMythicMob(shop.mythicmob))
                new RuntimeException(LanguageKey.ERROR_MYTHICMOBS_INVALID_ID.getMessage(shop.mythicmob)).printStackTrace();
        }
        String citizenId = yaml.getString("Npc.Options.Citizen");
        if (citizenId != null) {
            shop.uuid = UUID.fromString(citizenId);
            shop.citizen = shop.uuid;
            shop.npcType = NpcType.CITIZEN;
            if (CitizensHandler.isLoaded() && !CitizensHandler.isCitizensNPC(shop.uuid))
                new RuntimeException(LanguageKey.ERROR_MYTHICMOBS_INVALID_ID.getMessage(shop.uuid.toString())).printStackTrace();
        }
        shop.displayName = yaml.getString("Npc.Options.DisplayName");
        shop.invisible = yaml.getBoolean("Npc.Options.Invisible", false);
        shop.location.setYaw((float) yaml.getDouble("Npc.Status.Yaw", 0));
        shop.type = ShopType.valueOf(yaml.getString("Shop.Options.ShopType", "TwotoOne"));
        shop.lock = yaml.getBoolean("Npc.Status.Lock", false);
        shop.searchable = yaml.getBoolean("Npc.Status.Searchable", true);
        shop.equipments = new ObjectItems(yaml.get("Npc.Options.Equipments",
                IntStream.range(0, 6).mapToObj(i -> new ItemStack(Material.AIR)).collect(Collectors.toList())));
        shop.trades = yaml.getList("Trades", new ArrayList<>()).stream()
                .map(tradeconfig -> new ShopTrade((HashMap<String, Object>) tradeconfig))
                .collect(Collectors.toList());
        shop.updateTradeContents();
    }

    private static void applyNpcMetaFromShopkeepersConfig(Shop shop, ConfigurationSection section) {
        if (shop instanceof AgeableShop)
            ((AgeableShop) shop).setAgeLook(!section.getBoolean("baby", false));
        if (shop instanceof PoweredableShop)
            ((PoweredableShop) shop).setPowered(section.getBoolean("powered", false));
        if (shop instanceof HorseShop) {
            try {
                ((HorseShop) shop).setColor(Horse.Color.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(section.getString("color", "WHITE"), "WHITE")));
            } catch (Exception ignored) {}
            try {
                ((HorseShop) shop).setStyle(Horse.Style.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(section.getString("style", "NONE"), "NONE")));
            } catch (Exception ignored) {}
        }
        if (shop instanceof VillagerableShop) {
            com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler handler =
                    ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.VillagerHandlerProvider.getHandler();
            String professionName = section.getString("profession", section.getString("prof", handler.getDefaultProfessionName()));
            ((VillagerableShop) shop).setProfession(Villager.Profession.valueOf(handler.resolveProfession(professionName)));
            ((VillagerableShop) shop).setBiome(Villager.Type.valueOf(handler.resolveBiome(section.getString("villagerType", handler.getDefaultBiomeName()))));
            ((VillagerableShop) shop).setLevel(section.getInt("villagerLevel", 1));
        }
        if (shop instanceof ParrotShop) {
            try {
                ((ParrotShop) shop).setColor(Parrot.Variant.valueOf(ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(section.getString("parrotVariant", "RED"), "RED")));
            } catch (Exception ignored) {}
        }
        if (shop instanceof DyeableShop) {
            String color;
            try {
                Integer.parseInt(section.getString("color", "WHITE"));
                color = DyeColor.values()[section.getInt("color", 0)].name();
            } catch (NumberFormatException e) {
                color = ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.JavaUtil.cleanName(section.getString("color", "WHITE"), "WHITE");
            }
            try {
                ((DyeableShop) shop).setColor(DyeColor.valueOf(color));
            } catch (Exception ignored) {}
            ((DyeableShop) shop).setOptionalInfo(
                    (
                            section.contains("angry") ? section.getBoolean("angry", false) :
                                    (section.contains("sitting") ? section.getBoolean("sitting", false) :
                                            (section.getBoolean("shaved", false)))
                    )
            );
        }
    }
}
