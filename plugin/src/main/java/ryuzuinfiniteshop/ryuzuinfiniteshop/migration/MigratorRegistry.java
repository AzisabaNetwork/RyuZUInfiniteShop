package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MigratorRegistry {
    private static final List<MigrationStep> STEPS = new ArrayList<>();
    public static final int CURRENT_VERSION;

    static {
        register(new ProfessionKeyMigration());
        CURRENT_VERSION = STEPS.stream().mapToInt(MigrationStep::toVersion).max().orElse(0);
    }

    private static void register(MigrationStep step) {
        STEPS.add(step);
        STEPS.sort(Comparator.comparingInt(MigrationStep::fromVersion));
    }

    public static List<MigrationStep> getSteps() {
        return Collections.unmodifiableList(STEPS);
    }

    public static List<MigrationStep> getStepsFrom(int version) {
        List<MigrationStep> result = new ArrayList<>();
        for (MigrationStep step : STEPS) {
            if (step.fromVersion() >= version) result.add(step);
        }
        return result;
    }
}
