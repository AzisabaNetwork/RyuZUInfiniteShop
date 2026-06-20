package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopMigrationServiceTest {

    private static ServerMock server;
    private File shopsDir;
    private File backupDir;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        shopsDir = Files.createTempDirectory("sis-migration-test").toFile();
        backupDir = new File(shopsDir, "backup");
        cleanupShopsDir();
    }

    @AfterEach
    void afterEach() {
        cleanupShopsDir();
    }

    private void cleanupShopsDir() {
        if (shopsDir == null || !shopsDir.exists()) return;
        for (File f : shopsDir.listFiles()) {
            deleteRecursively(f);
        }
    }

    private void deleteRecursively(File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private File createShopFile(String name, String content) throws Exception {
        File f = new File(shopsDir, name);
        Files.writeString(f.toPath(), content);
        return f;
    }

    @Test
    @Order(1)
    void migrateOldProfessionKey() throws Exception {
        String yamlContent = "Npc.Options:\n" +
                "  EntityType: VILLAGER\n" +
                "  prof: NORMAL\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n";
        createShopFile("world_0_0_0.yml", yamlContent);

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(1, result.getMigrated(), "1 file should be migrated");
        assertEquals(0, result.getSkipped(), "0 files should be skipped");
        assertEquals(0, result.getFailed(), "0 files should fail");

        File migrated = new File(shopsDir, "world_0_0_0.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.load(migrated);

        assertEquals("NONE", config.getString("Npc.Options.Profession"), "prof NORMAL should map to Profession NONE");
        assertEquals("PLAINS", config.getString("Npc.Options.Biome"), "default biome should be PLAINS");
        assertEquals(1, config.getInt("Npc.Options.Level"), "default level should be 1");
        assertEquals(1, config.getInt("data-version"), "data-version should be 1");
        assertNull(config.getString("Npc.Options.prof"), "old prof key should be removed");

        // backup check
        File[] backups = backupDir.listFiles();
        assertNotNull(backups, "backup dir should exist");
        assertTrue(backups.length >= 1, "at least one backup dir should exist");
        File latestBackup = backups[0];
        File backupFile = new File(latestBackup, "world_0_0_0.yml");
        assertTrue(backupFile.exists(), "original file should be backed up");
        YamlConfiguration backupConfig = new YamlConfiguration();
        backupConfig.load(backupFile);
        assertEquals("NORMAL", backupConfig.getString("Npc.Options.prof"), "backup should preserve original prof");
    }

    @Test
    @Order(2)
    void idempotentForAlreadyMigrated() throws Exception {
        String yamlContent = "Npc.Options:\n" +
                "  EntityType: VILLAGER\n" +
                "  Profession: NONE\n" +
                "  Biome: PLAINS\n" +
                "  Level: 1\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n" +
                "data-version: 1\n";
        createShopFile("world_1_1_1.yml", yamlContent);

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(0, result.getMigrated(), "already-migrated file should not be migrated");
        assertEquals(1, result.getSkipped(), "already-migrated file should be skipped");
        assertEquals(0, result.getFailed(), "0 files should fail");

        // Ensure no backup created for skipped files
        File[] backups = backupDir.listFiles();
        if (backups != null) {
            for (File b : backups) {
                File bf = new File(b, "world_1_1_1.yml");
                assertFalse(bf.exists(), "skipped file should not be backed up");
            }
        }
    }

    @Test
    @Order(3)
    void migratePriestAndBlacksmith() throws Exception {
        createShopFile("world_2_2_2.yml",
                "Npc.Options:\n" +
                "  EntityType: VILLAGER\n" +
                "  prof: PRIEST\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n");
        createShopFile("world_3_3_3.yml",
                "Npc.Options:\n" +
                "  EntityType: ZOMBIE_VILLAGER\n" +
                "  prof: BLACKSMITH\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n");

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(2, result.getMigrated(), "2 files should be migrated");

        YamlConfiguration c2 = new YamlConfiguration();
        c2.load(new File(shopsDir, "world_2_2_2.yml"));
        assertEquals("CLERIC", c2.getString("Npc.Options.Profession"));

        YamlConfiguration c3 = new YamlConfiguration();
        c3.load(new File(shopsDir, "world_3_3_3.yml"));
        assertEquals("ARMORER", c3.getString("Npc.Options.Profession"));
    }

    @Test
    @Order(4)
    void doesNotOverwriteExistingProfession() throws Exception {
        createShopFile("world_4_4_4.yml",
                "Npc.Options:\n" +
                "  EntityType: VILLAGER\n" +
                "  prof: NORMAL\n" +
                "  Profession: LIBRARIAN\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n");

        ShopMigrationService.migrateAll(null, shopsDir);

        YamlConfiguration c = new YamlConfiguration();
        c.load(new File(shopsDir, "world_4_4_4.yml"));
        assertEquals("LIBRARIAN", c.getString("Npc.Options.Profession"), "existing Profession should be preserved");
    }

    @Test
    @Order(5)
    void nonVillagerWithoutProfIsMigratedForDataVersion() throws Exception {
        createShopFile("world_5_5_5.yml",
                "Npc.Options:\n" +
                "  EntityType: CREEPER\n" +
                "Npc.Status:\n" +
                "  Yaw: 0\n" +
                "Trades: []\n");

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(1, result.getMigrated(), "all files without data-version should be processed by v0->v1");
        assertEquals(0, result.getSkipped(), "no files should be skipped");

        YamlConfiguration c = new YamlConfiguration();
        c.load(new File(shopsDir, "world_5_5_5.yml"));
        assertEquals(1, c.getInt("data-version"), "data-version should be set to 1");
        assertNull(c.getString("Npc.Options.Profession"), "non-villager should not get Profession");
        assertNull(c.getString("Npc.Options.Biome"), "non-villager should not get Biome");
    }
}
