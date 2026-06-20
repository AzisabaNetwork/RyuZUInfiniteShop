package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigratorRegistryTest {

    @Test
    void currentVersionIsOne() {
        assertEquals(1, MigratorRegistry.CURRENT_VERSION);
    }

    @Test
    void stepsAreSortedByFromVersion() {
        List<MigrationStep> steps = MigratorRegistry.getSteps();
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(steps.get(i - 1).fromVersion() <= steps.get(i).fromVersion(),
                    "Steps must be sorted by fromVersion");
        }
    }

    @Test
    void firstStepIsProfessionKeyMigration() {
        List<MigrationStep> steps = MigratorRegistry.getSteps();
        assertFalse(steps.isEmpty());
        assertEquals(0, steps.get(0).fromVersion());
        assertEquals(1, steps.get(0).toVersion());
        assertInstanceOf(ProfessionKeyMigration.class, steps.get(0));
    }

    @Test
    void getStepsFromZeroReturnsAllSteps() {
        List<MigrationStep> steps = MigratorRegistry.getStepsFrom(0);
        assertEquals(MigratorRegistry.getSteps().size(), steps.size());
    }

    @Test
    void getStepsFromCurrentVersionReturnsEmptyList() {
        List<MigrationStep> steps = MigratorRegistry.getStepsFrom(MigratorRegistry.CURRENT_VERSION);
        assertTrue(steps.isEmpty());
    }

    @Test
    void registryIsImmutable() {
        List<MigrationStep> steps = MigratorRegistry.getSteps();
        assertThrows(UnsupportedOperationException.class, () -> steps.add(new NoOpStep(99, 100)));
    }

    private static final class NoOpStep implements MigrationStep {
        private final int from;
        private final int to;

        NoOpStep(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public int fromVersion() {
            return from;
        }

        @Override
        public int toVersion() {
            return to;
        }

        @Override
        public void migrate(YamlConfiguration yaml) {
            // no-op
        }

        @Override
        public String description() {
            return "no-op";
        }
    }
}
