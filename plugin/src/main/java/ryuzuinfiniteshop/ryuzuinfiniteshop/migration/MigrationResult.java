package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

public final class MigrationResult {
    private final int migrated;
    private final int skipped;
    private final int failed;

    public MigrationResult(int migrated, int skipped, int failed) {
        this.migrated = migrated;
        this.skipped = skipped;
        this.failed = failed;
    }

    public int getMigrated() { return migrated; }
    public int getSkipped() { return skipped; }
    public int getFailed() { return failed; }
}
