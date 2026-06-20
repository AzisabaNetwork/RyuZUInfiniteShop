package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;

public interface MigrationStep {
    int fromVersion();
    int toVersion();
    void migrate(YamlConfiguration yaml);
    String description();
}
