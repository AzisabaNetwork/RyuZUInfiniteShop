package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.DiffBackupUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                // Legacy heads/attributes must be rewritten before Bukkit deserializes (and drops) them.
                int rawVersion = rawDataVersion(text);
                if (rawVersion < LegacyMythicTypeMigration.TO_VERSION) text = LegacyMythicTypeMigration.fixRawYaml(text);
                if (rawVersion < LegacySkullMigration.TO_VERSION) text = LegacySkullMigration.fixRawYaml(text);
                if (rawVersion < LegacyAttributeMigration.TO_VERSION) text = LegacyAttributeMigration.fixRawYaml(text);
                yaml.loadFromString(text);
                int currentVersion = yaml.getInt("data-version", 0);

                if (currentVersion >= MigratorRegistry.CURRENT_VERSION) {
                    skipped++;
                    continue;
                }

                if (currentVersion < LegacyBackupMythicIdMigration.TO_VERSION) {
                    int restored = LegacyBackupMythicIdMigration.restore(yaml, shopsDir, file.getName());
                    if (restored > 0 && RyuZUInfiniteShop.getPlugin() != null)
                        RyuZUInfiniteShop.getPlugin().getLogger().info("Restored " + restored + " MythicMobs IDs of " + file.getName() + " from its 1.15.2 backup");
                }

                boolean fileChanged = false;
                for (MigrationStep step : MigratorRegistry.getStepsFrom(currentVersion)) {
                    if (step.fromVersion() >= currentVersion) {
                        step.migrate(yaml);
                        int migratedVersion = yaml.getInt("data-version", currentVersion);
                        if (migratedVersion >= step.toVersion()) {
                            currentVersion = migratedVersion;
                            fileChanged = true;
                        } else {
                            break;
                        }
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
                    // Recorded unconditionally: config.yml is not loaded yet at startup, and a gap here
                    // would break restoring from older history entries.
                    String id = file.getName().substring(0, file.getName().length() - ".yml".length());
                    DiffBackupUtil.write(file, yaml.saveToString(),
                            new File(shopsDir, "backup/" + id + ".diff"), "shops/" + file.getName());
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

    private static int rawDataVersion(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^data-version:\\s*(\\d+)").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static File createBackupDir(File shopsDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupDir = new File(shopsDir, "backup/" + timestamp);
        backupDir.mkdirs();
        return backupDir;
    }
}
