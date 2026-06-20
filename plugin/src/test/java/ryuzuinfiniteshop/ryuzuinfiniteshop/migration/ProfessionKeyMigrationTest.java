package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionKeyMigrationTest {

    private final ProfessionKeyMigration step = new ProfessionKeyMigration();

    @Test
    void versionRangeIsZeroToOne() {
        assertEquals(0, step.fromVersion());
        assertEquals(1, step.toVersion());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertNotNull(step.description());
        assertFalse(step.description().isEmpty());
    }

    @Test
    void migratesProfNormalToProfessionNone() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "NORMAL");

        step.migrate(yaml);

        assertEquals("NONE", yaml.getString("Npc.Options.Profession"));
        assertNull(yaml.getString("Npc.Options.prof"));
        assertEquals(1, yaml.getInt("data-version"));
    }

    @Test
    void migratesProfPriestToCleric() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "PRIEST");

        step.migrate(yaml);

        assertEquals("CLERIC", yaml.getString("Npc.Options.Profession"));
    }

    @Test
    void migratesProfBlacksmithToArmorer() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "ZOMBIE_VILLAGER");
        yaml.set("Npc.Options.prof", "BLACKSMITH");

        step.migrate(yaml);

        assertEquals("ARMORER", yaml.getString("Npc.Options.Profession"));
    }

    @Test
    void preservesUnknownProfessionName() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "FARMER");

        step.migrate(yaml);

        assertEquals("FARMER", yaml.getString("Npc.Options.Profession"));
    }

    @Test
    void caseInsensitiveLegacyNamesAreMapped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "normal");

        step.migrate(yaml);

        // Map lookup is case-sensitive; unknown values pass through unchanged
        assertEquals("normal", yaml.getString("Npc.Options.Profession"));
    }

    @Test
    void doesNotOverwriteExistingProfession() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "NORMAL");
        yaml.set("Npc.Options.Profession", "LIBRARIAN");

        step.migrate(yaml);

        assertEquals("LIBRARIAN", yaml.getString("Npc.Options.Profession"));
        assertNull(yaml.getString("Npc.Options.prof"));
    }

    @Test
    void fillsDefaultBiomeAndLevelForVillager() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "FARMER");

        step.migrate(yaml);

        assertEquals("PLAINS", yaml.getString("Npc.Options.Biome"));
        assertEquals(1, yaml.getInt("Npc.Options.Level"));
    }

    @Test
    void preservesExistingBiomeAndLevel() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "FARMER");
        yaml.set("Npc.Options.Biome", "DESERT");
        yaml.set("Npc.Options.Level", 3);

        step.migrate(yaml);

        assertEquals("DESERT", yaml.getString("Npc.Options.Biome"));
        assertEquals(3, yaml.getInt("Npc.Options.Level"));
    }

    @Test
    void doesNotAddProfessionOrBiomeToNonVillagerWithoutProf() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "CREEPER");

        step.migrate(yaml);

        assertNull(yaml.getString("Npc.Options.Profession"));
        assertNull(yaml.getString("Npc.Options.Biome"));
        assertEquals(1, yaml.getInt("data-version"));
    }

    @Test
    void addsProfessionBiomeLevelToZombieVillagerWithProf() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "ZOMBIE_VILLAGER");
        yaml.set("Npc.Options.prof", "PRIEST");

        step.migrate(yaml);

        assertEquals("CLERIC", yaml.getString("Npc.Options.Profession"));
        assertEquals("PLAINS", yaml.getString("Npc.Options.Biome"));
        assertEquals(1, yaml.getInt("Npc.Options.Level"));
    }

    @Test
    void emptyProfIsTreatedAsValue() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Npc.Options.EntityType", "VILLAGER");
        yaml.set("Npc.Options.prof", "");

        step.migrate(yaml);

        assertEquals("", yaml.getString("Npc.Options.Profession"));
        assertNull(yaml.getString("Npc.Options.prof"));
    }
}
