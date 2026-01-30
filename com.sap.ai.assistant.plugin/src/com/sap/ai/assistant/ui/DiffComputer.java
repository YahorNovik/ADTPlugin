package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes a unified diff between two texts using a simple LCS-based algorithm.
 * Suitable for ABAP source sizes (typically hundreds to low thousands of lines).
 */
public final class DiffComputer {

    public enum LineType { CONTEXT, ADDED, REMOVED }

    public static class DiffLine {
        public final LineType type;
        public final String text;

        DiffLine(LineType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private DiffComputer() {}

    /**
     * Compute diff lines between old and new source with context lines around changes.
     */
    public static List<DiffLine> computeDiff(String oldText, String newText, int contextLines) {
        String[] oldLines = splitLines(oldText);
        String[] newLines = splitLines(newText);

        // Compute LCS table
        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    lcs[i][j] = 1 + lcs[i + 1][j + 1];
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        // Backtrack to produce raw diff
        List<DiffLine> raw = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && oldLines[i].equals(newLines[j])) {
                raw.add(new DiffLine(LineType.CONTEXT, oldLines[i]));
                i++;
                j++;
            } else if (j < n && (i >= m || lcs[i][j + 1] >= lcs[i + 1][j])) {
                raw.add(new DiffLine(LineType.ADDED, newLines[j]));
                j++;
            } else {
                raw.add(new DiffLine(LineType.REMOVED, oldLines[i]));
                i++;
            }
        }

        // Filter to show only changed lines with surrounding context
        return filterWithContext(raw, contextLines);
    }

    /**
     * Format diff lines as a unified diff string.
     */
    public static String formatUnifiedDiff(List<DiffLine> lines, String fileName) {
        if (lines.isEmpty()) {
            return "(no changes)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(fileName).append('\n');
        sb.append("+++ b/").append(fileName).append('\n');

        for (DiffLine line : lines) {
            switch (line.type) {
                case CONTEXT:
                    sb.append("  ").append(line.text).append('\n');
                    break;
                case REMOVED:
                    sb.append("- ").append(line.text).append('\n');
                    break;
                case ADDED:
                    sb.append("+ ").append(line.text).append('\n');
                    break;
            }
        }
        return sb.toString();
    }

    // -- Internal helpers --

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\\r?\\n", -1);
    }

    /**
     * Keep only changed lines and their surrounding context lines.
     * Insert a separator marker between non-adjacent hunks.
     */
    private static List<DiffLine> filterWithContext(List<DiffLine> raw, int contextLines) {
        int size = raw.size();
        boolean[] include = new boolean[size];

        // Mark changed lines and their context
        for (int k = 0; k < size; k++) {
            if (raw.get(k).type != LineType.CONTEXT) {
                int start = Math.max(0, k - contextLines);
                int end = Math.min(size - 1, k + contextLines);
                for (int c = start; c <= end; c++) {
                    include[c] = true;
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        boolean lastIncluded = false;
        for (int k = 0; k < size; k++) {
            if (include[k]) {
                if (!lastIncluded && !result.isEmpty()) {
                    // Gap between hunks
                    result.add(new DiffLine(LineType.CONTEXT, "..."));
                }
                result.add(raw.get(k));
                lastIncluded = true;
            } else {
                lastIncluded = false;
            }
        }

        return result;
    }
}
