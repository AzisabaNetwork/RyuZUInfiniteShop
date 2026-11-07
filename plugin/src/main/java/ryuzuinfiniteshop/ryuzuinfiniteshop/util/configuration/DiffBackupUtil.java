package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes data files while keeping a change history: whenever the new content differs
 * from what is on disk, a unified diff of the change is appended to a backup file.
 */
public final class DiffBackupUtil {
    static final int CONTEXT = 3;
    /** Upper bound on LCS table size; larger changed regions are recorded as a full replacement. */
    private static final long MAX_LCS_CELLS = 4_000_000L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object appendLock = new Object();

    private DiffBackupUtil() {}

    /** The diff history file for {@code name} ({@code shops/backup/<name>.diff}), or null when disabled. */
    public static File backupFile(String name) {
        if (!Config.backupDiff) return null;
        return historyFile(name);
    }

    /** The diff history file for {@code name}, regardless of whether recording is enabled. */
    public static File historyFile(String name) {
        return new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), "shops/backup/" + name + ".diff");
    }

    /**
     * Writes {@code newContent} to {@code target}. If {@code backupFile} is non-null and the content
     * changed, the diff from the previous content is appended to {@code backupFile} first.
     */
    public static void write(File target, String newContent, File backupFile, String label) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create directory " + parent);
        if (backupFile != null) {
            String oldContent = target.isFile() ? Files.readString(target.toPath(), StandardCharsets.UTF_8) : "";
            appendDiff(backupFile, label, oldContent, newContent);
        }
        Files.writeString(target.toPath(), newContent, StandardCharsets.UTF_8);
    }

    /** Records the removal of {@code target} (its whole content as deleted lines) in {@code backupFile}. */
    public static void recordDeletion(File target, File backupFile, String label) throws IOException {
        if (!target.isFile()) return;
        appendDiff(backupFile, label, Files.readString(target.toPath(), StandardCharsets.UTF_8), "");
    }

    private static void appendDiff(File backupFile, String label, String oldContent, String newContent) throws IOException {
        String diff = unifiedDiff(label, splitLines(oldContent), splitLines(newContent));
        if (diff.isEmpty()) return;
        String entry = "=== " + LocalDateTime.now().format(TIMESTAMP) + " ===" + System.lineSeparator() + diff;
        synchronized (appendLock) {
            File parent = backupFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new IOException("Could not create directory " + parent);
            Files.writeString(backupFile.toPath(), entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    static List<String> splitLines(String content) {
        if (content.isEmpty()) return List.of();
        String[] parts = content.split("\r?\n", -1);
        int length = content.endsWith("\n") ? parts.length - 1 : parts.length;
        return Arrays.asList(parts).subList(0, length);
    }

    // ========== Reading history and restoring ==========

    /** One {@code @@} block of a recorded diff; {@code lines} keep their ' ', '-' or '+' prefix. */
    public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {}

    /** One recorded save: the timestamp header and the hunks it changed. */
    public record Entry(String timestamp, List<Hunk> hunks) {
        public int added() {
            return (int) hunks.stream().flatMap(h -> h.lines.stream()).filter(l -> l.startsWith("+")).count();
        }

        public int removed() {
            return (int) hunks.stream().flatMap(h -> h.lines.stream()).filter(l -> l.startsWith("-")).count();
        }
    }

    private static final java.util.regex.Pattern ENTRY_HEADER = java.util.regex.Pattern.compile("^=== (.+) ===$");
    private static final java.util.regex.Pattern HUNK_HEADER =
            java.util.regex.Pattern.compile("^@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@$");

    /** Reads every entry of a history file, oldest first; an absent file has no entries. */
    public static List<Entry> readEntries(File backupFile) throws IOException {
        if (backupFile == null || !backupFile.isFile()) return List.of();
        return parseEntries(Files.readString(backupFile.toPath(), StandardCharsets.UTF_8));
    }

    static List<Entry> parseEntries(String content) {
        List<String> lines = splitLines(content);
        List<Entry> entries = new ArrayList<>();
        String timestamp = null;
        List<Hunk> hunks = null;
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            java.util.regex.Matcher entry = ENTRY_HEADER.matcher(line);
            java.util.regex.Matcher hunk = HUNK_HEADER.matcher(line);
            if (entry.matches()) {
                if (timestamp != null) entries.add(new Entry(timestamp, hunks));
                timestamp = entry.group(1);
                hunks = new ArrayList<>();
                i++;
            } else if (hunk.matches() && hunks != null) {
                int oldCount = Integer.parseInt(hunk.group(2));
                int newCount = Integer.parseInt(hunk.group(4));
                List<String> body = new ArrayList<>();
                int oldLeft = oldCount, newLeft = newCount;
                i++;
                // Consume by count so content lines such as "--- x" are never mistaken for headers.
                while ((oldLeft > 0 || newLeft > 0) && i < lines.size()) {
                    String l = lines.get(i++);
                    char t = l.isEmpty() ? ' ' : l.charAt(0);
                    if (t != '+') oldLeft--;
                    if (t != '-') newLeft--;
                    body.add(l.isEmpty() ? " " : l);
                }
                if (oldLeft != 0 || newLeft != 0)
                    throw new IllegalStateException("Truncated hunk in entry " + timestamp);
                hunks.add(new Hunk(Integer.parseInt(hunk.group(1)), oldCount,
                        Integer.parseInt(hunk.group(3)), newCount, body));
            } else {
                i++; // "--- a/" / "+++ b/" file headers
            }
        }
        if (timestamp != null) entries.add(new Entry(timestamp, hunks));
        return entries;
    }

    /**
     * Rolls {@code current} back to the state right after entry number {@code keep}
     * (1-based; 0 means before the first entry) by undoing every later entry, newest first.
     *
     * @throws IllegalStateException when the history does not match the content
     */
    public static String restore(String current, List<Entry> entries, int keep) {
        if (keep < 0 || keep > entries.size())
            throw new IllegalArgumentException("Entry " + keep + " is out of range 0-" + entries.size());
        List<String> lines = new ArrayList<>(splitLines(current));
        for (int e = entries.size() - 1; e >= keep; e--) {
            lines = reverseApply(lines, entries.get(e));
        }
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private static List<String> reverseApply(List<String> lines, Entry entry) {
        List<String> result = new ArrayList<>(lines);
        // Bottom-up, so positions of earlier hunks (relative to the new file) stay valid.
        for (int h = entry.hunks.size() - 1; h >= 0; h--) {
            Hunk hunk = entry.hunks.get(h);
            List<String> newSide = new ArrayList<>();
            List<String> oldSide = new ArrayList<>();
            for (String l : hunk.lines) {
                char t = l.charAt(0);
                if (t != '-') newSide.add(l.substring(1));
                if (t != '+') oldSide.add(l.substring(1));
            }
            int at = hunk.newCount == 0 ? hunk.newStart : hunk.newStart - 1;
            if (at < 0 || at + newSide.size() > result.size()
                    || !result.subList(at, at + newSide.size()).equals(newSide))
                throw new IllegalStateException("History entry " + entry.timestamp + " does not match the file");
            List<String> target = result.subList(at, at + newSide.size());
            target.clear();
            target.addAll(oldSide);
        }
        return result;
    }

    private record Op(char type, int oldPos, int newPos, String text) {}

    /** Returns a unified diff of the two line lists, or an empty string when they are equal. */
    static String unifiedDiff(String label, List<String> a, List<String> b) {
        List<Op> ops = diffOps(a, b);
        String nl = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        int n = ops.size();
        int idx = 0;
        while (true) {
            int firstChange = -1;
            for (int k = idx; k < n; k++) {
                if (ops.get(k).type != ' ') {
                    firstChange = k;
                    break;
                }
            }
            if (firstChange < 0) break;

            int start = Math.max(idx, firstChange - CONTEXT);
            int lastChange = firstChange;
            for (int k = firstChange + 1; k < n; k++) {
                if (ops.get(k).type != ' ') lastChange = k;
                else if (k - lastChange > 2 * CONTEXT) break;
            }
            int end = Math.min(n, lastChange + 1 + CONTEXT);

            int oldCount = 0, newCount = 0;
            for (int k = start; k < end; k++) {
                char t = ops.get(k).type;
                if (t != '+') oldCount++;
                if (t != '-') newCount++;
            }
            Op first = ops.get(start);
            int oldStart = oldCount == 0 ? first.oldPos : first.oldPos + 1;
            int newStart = newCount == 0 ? first.newPos : first.newPos + 1;

            if (out.isEmpty()) {
                out.append("--- a/").append(label).append(nl);
                out.append("+++ b/").append(label).append(nl);
            }
            out.append("@@ -").append(oldStart).append(',').append(oldCount)
                    .append(" +").append(newStart).append(',').append(newCount).append(" @@").append(nl);
            for (int k = start; k < end; k++) {
                Op op = ops.get(k);
                out.append(op.type).append(op.text).append(nl);
            }
            idx = end;
        }
        return out.toString();
    }

    /** Line-level edit script: common prefix/suffix are trimmed, the middle is aligned by LCS. */
    private static List<Op> diffOps(List<String> a, List<String> b) {
        int prefix = 0;
        int max = Math.min(a.size(), b.size());
        while (prefix < max && a.get(prefix).equals(b.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < max - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) suffix++;

        List<Op> ops = new ArrayList<>(a.size() + b.size());
        for (int k = 0; k < prefix; k++) ops.add(new Op(' ', k, k, a.get(k)));

        int aEnd = a.size() - suffix, bEnd = b.size() - suffix;
        int n = aEnd - prefix, m = bEnd - prefix;
        int i = prefix, j = prefix;
        if ((long) (n + 1) * (m + 1) <= MAX_LCS_CELLS) {
            // lcs[x][y] = LCS length of a[prefix+x..aEnd) and b[prefix+y..bEnd)
            int[][] lcs = new int[n + 1][m + 1];
            for (int x = n - 1; x >= 0; x--)
                for (int y = m - 1; y >= 0; y--)
                    lcs[x][y] = a.get(prefix + x).equals(b.get(prefix + y))
                            ? lcs[x + 1][y + 1] + 1
                            : Math.max(lcs[x + 1][y], lcs[x][y + 1]);
            while (i < aEnd && j < bEnd) {
                int x = i - prefix, y = j - prefix;
                if (a.get(i).equals(b.get(j))) {
                    ops.add(new Op(' ', i, j, a.get(i)));
                    i++;
                    j++;
                } else if (lcs[x + 1][y] >= lcs[x][y + 1]) {
                    ops.add(new Op('-', i, j, a.get(i)));
                    i++;
                } else {
                    ops.add(new Op('+', i, j, b.get(j)));
                    j++;
                }
            }
        }
        while (i < aEnd) {
            ops.add(new Op('-', i, j, a.get(i)));
            i++;
        }
        while (j < bEnd) {
            ops.add(new Op('+', i, j, b.get(j)));
            j++;
        }

        for (int k = 0; k < suffix; k++) ops.add(new Op(' ', aEnd + k, bEnd + k, a.get(aEnd + k)));
        return ops;
    }
}
