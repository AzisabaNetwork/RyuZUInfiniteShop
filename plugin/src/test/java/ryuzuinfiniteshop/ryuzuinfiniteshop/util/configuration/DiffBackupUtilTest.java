package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffBackupUtilTest {

    private static final String NL = System.lineSeparator();

    @Test
    void equalContentProducesNoDiff() {
        assertEquals("", DiffBackupUtil.unifiedDiff("x.yml", List.of("a", "b"), List.of("a", "b")));
    }

    @Test
    void singleLineChangeIsReportedWithContext() {
        List<String> a = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        List<String> b = List.of("1", "2", "3", "4", "X", "6", "7", "8", "9");
        String expected = "--- a/x.yml" + NL
                + "+++ b/x.yml" + NL
                + "@@ -2,7 +2,7 @@" + NL
                + " 2" + NL + " 3" + NL + " 4" + NL
                + "-5" + NL + "+X" + NL
                + " 6" + NL + " 7" + NL + " 8" + NL;
        assertEquals(expected, DiffBackupUtil.unifiedDiff("x.yml", a, b));
    }

    @Test
    void distantChangesProduceSeparateHunks() {
        List<String> a = new ArrayList<>();
        for (int i = 1; i <= 20; i++) a.add(String.valueOf(i));
        List<String> b = new ArrayList<>(a);
        b.set(0, "A");
        b.set(19, "T");
        String diff = DiffBackupUtil.unifiedDiff("x.yml", a, b);
        assertTrue(diff.contains("@@ -1,4 +1,4 @@"), diff);
        assertTrue(diff.contains("@@ -17,4 +17,4 @@"), diff);
    }

    @Test
    void insertionIntoEmptyFile() {
        String diff = DiffBackupUtil.unifiedDiff("x.yml", List.of(), List.of("a", "b"));
        assertTrue(diff.contains("@@ -0,0 +1,2 @@" + NL + "+a" + NL + "+b" + NL), diff);
    }

    @Test
    void writeAppendsDiffOnlyWhenContentChanges(@TempDir Path dir) throws Exception {
        File target = dir.resolve("shops/s.yml").toFile();
        File backup = dir.resolve("shops/backup/s.diff").toFile();

        DiffBackupUtil.write(target, "a: 1\n", backup, "shops/s.yml");
        assertEquals("a: 1\n", Files.readString(target.toPath(), StandardCharsets.UTF_8));
        String first = Files.readString(backup.toPath(), StandardCharsets.UTF_8);
        assertTrue(first.contains("+a: 1"), first);

        DiffBackupUtil.write(target, "a: 1\n", backup, "shops/s.yml");
        assertEquals(first, Files.readString(backup.toPath(), StandardCharsets.UTF_8), "unchanged save must not append");

        DiffBackupUtil.write(target, "a: 2\n", backup, "shops/s.yml");
        String second = Files.readString(backup.toPath(), StandardCharsets.UTF_8);
        assertTrue(second.startsWith(first), "history must be appended");
        assertTrue(second.substring(first.length()).contains("-a: 1" + NL + "+a: 2"), second);
    }

    @Test
    void writeWithoutBackupFileSkipsHistory(@TempDir Path dir) throws Exception {
        File target = dir.resolve("s.yml").toFile();
        DiffBackupUtil.write(target, "a: 1\n", null, "s.yml");
        assertEquals("a: 1\n", Files.readString(target.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void restoreRollsBackToAnyEntry(@TempDir Path dir) throws Exception {
        File target = dir.resolve("s.yml").toFile();
        File backup = dir.resolve("s.diff").toFile();
        String v1 = "a: 1\nb: 1\nc: 1\n";
        String v2 = "a: 2\nb: 1\nc: 1\nd: 1\n";
        String v3 = "--- x\nb: 1\nc: 3\n";  // content that looks like a diff header
        DiffBackupUtil.write(target, v1, backup, "s.yml");
        DiffBackupUtil.write(target, v2, backup, "s.yml");
        DiffBackupUtil.write(target, v3, backup, "s.yml");
        DiffBackupUtil.recordDeletion(target, backup, "s.yml");
        Files.delete(target.toPath());

        List<DiffBackupUtil.Entry> entries = DiffBackupUtil.readEntries(backup);
        assertEquals(4, entries.size());
        assertEquals("", DiffBackupUtil.restore("", entries, 4));
        assertEquals(v3, DiffBackupUtil.restore("", entries, 3));
        assertEquals(v2, DiffBackupUtil.restore("", entries, 2));
        assertEquals(v1, DiffBackupUtil.restore("", entries, 1));
        assertEquals("", DiffBackupUtil.restore("", entries, 0));
    }

    @Test
    void restoreRejectsContentThatDoesNotMatchHistory(@TempDir Path dir) throws Exception {
        File target = dir.resolve("s.yml").toFile();
        File backup = dir.resolve("s.diff").toFile();
        DiffBackupUtil.write(target, "a: 1\n", backup, "s.yml");
        DiffBackupUtil.write(target, "a: 2\n", backup, "s.yml");
        List<DiffBackupUtil.Entry> entries = DiffBackupUtil.readEntries(backup);
        assertThrows(IllegalStateException.class, () -> DiffBackupUtil.restore("a: 9\n", entries, 1));
    }

    @Test
    void entryCountsAddedAndRemovedLines() {
        String history = "=== 2026-10-03 10:00:00 ===" + NL
                + DiffBackupUtil.unifiedDiff("s.yml", List.of("a", "b"), List.of("a", "c", "d"));
        DiffBackupUtil.Entry entry = DiffBackupUtil.parseEntries(history).get(0);
        assertEquals("2026-10-03 10:00:00", entry.timestamp());
        assertEquals(2, entry.added());
        assertEquals(1, entry.removed());
    }

    @Test
    void recordDeletionAppendsRemovedLines(@TempDir Path dir) throws Exception {
        File target = dir.resolve("s.yml").toFile();
        File backup = dir.resolve("s.diff").toFile();
        Files.writeString(target.toPath(), "a: 1\nb: 2\n");
        DiffBackupUtil.recordDeletion(target, backup, "s.yml");
        String diff = Files.readString(backup.toPath(), StandardCharsets.UTF_8);
        assertTrue(diff.contains("@@ -1,2 +0,0 @@" + NL + "-a: 1" + NL + "-b: 2"), diff);
    }
}
