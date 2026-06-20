package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

public class ProfessionKeyMigration implements MigrationStep {
    private static final Map<String, String> PROFESSION_LEGACY_MAP = Map.of(
        "NORMAL", "NONE",
        "PRIEST", "CLERIC",
        "BLACKSMITH", "ARMORER"
    );

    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
    }

    @Override
    public String description() {
        return "Migrate old 'prof' key to 'Profession', map legacy profession names, and fill default Biome/Level";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        String prof = yaml.getString("Npc.Options.prof", null);
        String existingProfession = yaml.getString("Npc.Options.Profession", null);

        // Only migrate if old prof exists and Profession is not already set
        if (prof != null && existingProfession == null) {
            String mapped = PROFESSION_LEGACY_MAP.getOrDefault(prof, prof);
            yaml.set("Npc.Options.Profession", mapped);
        }

        // Remove old prof key if it exists
        if (prof != null) {
            yaml.set("Npc.Options.prof", null);
        }

        // Fill defaults for villager-like shops (only if profession-related keys exist or entity type is villager)
        boolean isVillager = isVillagerLike(yaml);
        if (isVillager) {
            if (yaml.getString("Npc.Options.Biome", null) == null) {
                yaml.set("Npc.Options.Biome", "PLAINS");
            }
            if (!yaml.contains("Npc.Options.Level")) {
                yaml.set("Npc.Options.Level", 1);
            }
        }

        // Always set data-version to mark this file as processed through v0->v1
        yaml.set("data-version", 1);
    }

    private boolean isVillagerLike(YamlConfiguration yaml) {
        String entityType = yaml.getString("Npc.Options.EntityType", "");
        return entityType.equalsIgnoreCase("VILLAGER")
                || entityType.equalsIgnoreCase("ZOMBIE_VILLAGER")
                || yaml.getString("Npc.Options.prof", null) != null
                || yaml.getString("Npc.Options.Profession", null) != null;
    }
}
