package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ShopMigrationService {

    private ShopMigrationService() {}

    public static MigrationResult migrateAll(CommandSender sender) {
        File shopsDir = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops");
        return migrateAll(sender, shopsDir);
    }

    static MigrationResult migrateAll(CommandSender sender, File shopsDir) {
        if (!shopsDir.exists() || !shopsDir.isDirectory()) {
            return new MigrationResult(0, 0, 0);
        }

        File[] files = shopsDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("save.yml"));
        if (files == null || files.length == 0) {
            return new MigrationResult(0, 0, 0);
        }

        int migrated = 0;
        int skipped = 0;
        int failed = 0;
        File backupDir = null;
        boolean anyMigrated = false;

        for (File file : files) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                int currentVersion = yaml.getInt("data-version", 0);

                if (currentVersion >= MigratorRegistry.CURRENT_VERSION) {
                    skipped++;
                    continue;
                }

                boolean fileChanged = false;
                for (MigrationStep step : MigratorRegistry.getStepsFrom(currentVersion)) {
                    if (step.fromVersion() >= currentVersion) {
                        step.migrate(yaml);
                        currentVersion = step.toVersion();
                        fileChanged = true;
                    }
                }

                if (fileChanged) {
                    if (!anyMigrated) {
                        backupDir = createBackupDir(shopsDir);
                        anyMigrated = true;
                    }
                    if (backupDir != null) {
                        Files.copy(file.toPath(), new File(backupDir, file.getName()).toPath());
                    }
                    yaml.save(file);
                    migrated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                if (sender != null) {
                    sender.sendMessage(RyuZUInfiniteShop.prefixCommand + "§cMigration failed for " + file.getName() + ": " + e.getMessage());
                }
                if (RyuZUInfiniteShop.getPlugin() != null && RyuZUInfiniteShop.getPlugin().getLogger() != null) {
                    RyuZUInfiniteShop.getPlugin().getLogger().warning("Migration failed for " + file.getName() + ": " + e.getMessage());
                }
                e.printStackTrace();
            }
        }

        if (migrated > 0 && sender != null) {
            sender.sendMessage(RyuZUInfiniteShop.prefixCommand + "§aMigration complete: " + migrated + " migrated, " + skipped + " skipped, " + failed + " failed.");
        }
        return new MigrationResult(migrated, skipped, failed);
    }

    private static File createBackupDir(File shopsDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupDir = new File(shopsDir, "backup/" + timestamp);
        backupDir.mkdirs();
        return backupDir;
    }
}
