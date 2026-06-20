package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ShopMigrationServiceErrorTest {

    private File shopsDir;

    @BeforeEach
    void setUp() throws IOException {
        shopsDir = Files.createTempDirectory("sis-migration-error-test").toFile();
    }

    @AfterEach
    void tearDown() {
        deleteRecursively(shopsDir);
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

    @Test
    void returnsEmptyResultWhenDirectoryDoesNotExist() {
        File missingDir = new File(shopsDir, "missing");
        MigrationResult result = ShopMigrationService.migrateAll(null, missingDir);

        assertEquals(0, result.getMigrated());
        assertEquals(0, result.getSkipped());
        assertEquals(0, result.getFailed());
    }

    @Test
    void returnsEmptyResultWhenDirectoryIsEmpty() {
        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(0, result.getMigrated());
        assertEquals(0, result.getSkipped());
        assertEquals(0, result.getFailed());
    }

    @Test
    void excludesSaveYmlFromMigration() throws IOException {
        Files.writeString(new File(shopsDir, "save.yml").toPath(),
                "Npc.Options:\n  prof: NORMAL\n");

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(0, result.getMigrated());
        assertEquals(0, result.getSkipped());
        assertEquals(0, result.getFailed());
    }

    @Test
    void countsInvalidYamlAsFailed() throws IOException {
        Files.writeString(new File(shopsDir, "invalid.yml").toPath(),
                "this is not: valid yaml: [");

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(0, result.getMigrated());
        assertEquals(0, result.getSkipped());
        assertEquals(1, result.getFailed());
    }

    @Test
    void continuesProcessingAfterFailedFile() throws IOException {
        Files.writeString(new File(shopsDir, "invalid.yml").toPath(),
                "this is not: valid yaml: [");
        Files.writeString(new File(shopsDir, "valid.yml").toPath(),
                "Npc.Options:\n  EntityType: VILLAGER\n  prof: NORMAL\n");

        MigrationResult result = ShopMigrationService.migrateAll(null, shopsDir);

        assertEquals(1, result.getMigrated());
        assertEquals(0, result.getSkipped());
        assertEquals(1, result.getFailed());
    }
}
