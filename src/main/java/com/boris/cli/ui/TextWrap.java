package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

public final class TextWrap {

    private TextWrap() {
    }

    public static String wrap(String text, int width) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                out.append('\n');
                continue;
            }
            if (isDiffLine(rawLine)) {
                List<String> wrapped = wrapDiffLine(rawLine, width);
                for (String piece : wrapped) {
                    out.append(piece).append('\n');
                }
                continue;
            }
            List<String> pieces = wrapLine(rawLine, width);
            for (String piece : pieces) {
                out.append(piece).append('\n');
            }
        }
        if (out.length() > 0) {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static boolean isDiffLine(String line) {
        if (line.matches("^\\s*\\d+\\s*│[+-]\\s.*") || line.matches("^\\s*\\d+\\s*\\|[+-]\\s.*")) {
            return true;
        }
        if (line.startsWith("+ ") || (line.startsWith("- ") && !line.startsWith("- [ ]") && !line.startsWith("- [x]"))) {
            return true;
        }
        if (isTextLabelLine(line)) {
            return true;
        }
        if (line.startsWith("@@@") && line.endsWith("@@@")) {
            return true;
        }
        if (line.matches("^─+\\s*\\+\\d+\\s+-\\d+\\s*─+$")) {
            return true;
        }
        return false;
    }

    /**
     * Marker prefix for diff continuation lines.
     * Format: DIFF_CONT_MARKER + ('+' or '-') + content
     */
    public static final String DIFF_CONT_MARKER = "​​";

    private static final java.util.regex.Pattern TEXT_LABEL_PATTERN =
        java.util.regex.Pattern.compile("(?i)^(\\s*(?:[-*+]?\\s*)?(?:\\*{0,2}\\s*(?:old|new)[ _-]?text\\b[^:]*:)\\s*)(.*)$");

    private static boolean isTextLabelLine(String line) {
        return TEXT_LABEL_PATTERN.matcher(line).find();
    }

    private static List<String> wrapDiffLine(String line, int width) {
        List<String> result = new ArrayList<>();

        // Detect diff line format: "  10 │+ code" or "  10 |+ code"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s*\\d+\\s*[│|])([+-])(\\s.*)$");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
            String prefix = matcher.group(1);  // "  10 │"
            String symbol = matcher.group(2);  // "+" or "-"
            String content = matcher.group(3); // " code..."

            int prefixLen = prefix.length() + 1; // +1 for symbol
            int contentWidth = width - prefixLen - 1; // -1 for the space after symbol

            if (contentWidth <= 5) {
                // Too narrow, just truncate with ellipsis
                result.add(truncateWithEllipsis(line, width));
                return result;
            }

            // First line keeps full prefix
            if (content.length() <= contentWidth + 1) {
                result.add(line);
                return result;
            }

            // Wrap the content
            String remaining = content.substring(1); // Remove leading space
            boolean first = true;
            String indent = " ".repeat(prefixLen + 1); // Align with code content

            while (!remaining.isEmpty()) {
                int cutWidth = first ? contentWidth : (width - indent.length());
                if (cutWidth <= 0) cutWidth = 1;

                String chunk;
                if (remaining.length() <= cutWidth) {
                    chunk = remaining;
                    remaining = "";
                } else {
                    // Try to break at word boundary
                    int breakPoint = findWordBreak(remaining, cutWidth);
                    chunk = remaining.substring(0, breakPoint);
                    remaining = remaining.substring(breakPoint).stripLeading();
                }

                if (first) {
                    result.add(prefix + symbol + " " + chunk);
                    first = false;
                } else {
                    // Continuation line with marker for renderer
                    result.add(DIFF_CONT_MARKER + symbol + indent.substring(2) + chunk);
                }
            }
            return result;
        }

        // Simple diff line: "+ code" or "- code"
        if ((line.startsWith("+ ") || (line.startsWith("- ") && !line.startsWith("- [ ]") && !line.startsWith("- [x]")))) {
            String symbol = line.substring(0, 1);
            String content = line.substring(2);
            int contentWidth = width - 2;

            if (contentWidth <= 3) {
                result.add(truncateWithEllipsis(line, width));
                return result;
            }

            if (content.length() <= contentWidth) {
                result.add(line);
                return result;
            }

            String remaining = content;
            boolean first = true;

            while (!remaining.isEmpty()) {
                int cutWidth = first ? contentWidth : (width - 2);
                if (cutWidth <= 0) cutWidth = 1;

                String chunk;
                if (remaining.length() <= cutWidth) {
                    chunk = remaining;
                    remaining = "";
                } else {
                    int breakPoint = findWordBreak(remaining, cutWidth);
                    chunk = remaining.substring(0, breakPoint);
                    remaining = remaining.substring(breakPoint).stripLeading();
                }

                if (first) {
                    result.add(symbol + " " + chunk);
                    first = false;
                } else {
                    result.add(DIFF_CONT_MARKER + symbol + "  " + chunk);
                }
            }
            return result;
        }

        // Old Text / New Text label lines
        java.util.regex.Matcher labelMatcher = TEXT_LABEL_PATTERN.matcher(line);
        if (labelMatcher.matches()) {
            boolean isAdd = labelMatcher.group(1).toLowerCase().matches(".*\\bnew\\b.*");
            String symbol = isAdd ? "+" : "-";
            String label = labelMatcher.group(1);
            String content = labelMatcher.group(2);
            int contentWidth = width - label.length();

            if (contentWidth <= 3) {
                result.add(truncateWithEllipsis(line, width));
                return result;
            }

            if (content.length() <= contentWidth) {
                result.add(line);
                return result;
            }

            String remaining = content;
            boolean first = true;
            String indent = " ".repeat(label.length());

            while (!remaining.isEmpty()) {
                int cutWidth = first ? contentWidth : (width - indent.length());
                if (cutWidth <= 0) cutWidth = 1;

                String chunk;
                if (remaining.length() <= cutWidth) {
                    chunk = remaining;
                    remaining = "";
                } else {
                    int breakPoint = findWordBreak(remaining, cutWidth);
                    chunk = remaining.substring(0, breakPoint);
                    remaining = remaining.substring(breakPoint).stripLeading();
                }

                if (first) {
                    result.add(label + chunk);
                    first = false;
                } else {
                    result.add(DIFF_CONT_MARKER + symbol + indent + chunk);
                }
            }
            return result;
        }

        // Non-wrappable diff lines (headers, stats)
        result.add(truncateWithEllipsis(line, width));
        return result;
    }

    private static int findWordBreak(String text, int maxWidth) {
        if (text.length() <= maxWidth) {
            return text.length();
        }
        // Look for last space before maxWidth
        int lastSpace = text.lastIndexOf(' ', maxWidth);
        if (lastSpace > maxWidth / 3) {
            return lastSpace;
        }
        // No good break point, hard cut
        return maxWidth;
    }

    private static String truncateWithEllipsis(String line, int width) {
        if (line.length() <= width) {
            return line;
        }
        if (width <= 3) {
            return line.substring(0, width);
        }
        return line.substring(0, width - 3) + "...";
    }

    public static List<String> wrapLine(String line, int width) {
        List<String> result = new ArrayList<>();
        if (line.length() <= width) {
            result.add(line);
            return result;
        }
        String[] words = line.split(" ", -1);
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.add(word.substring(0, width));
                word = word.substring(width);
            }
            int extra = current.length() == 0 ? 0 : 1;
            if (current.length() + extra + word.length() > width) {
                result.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        result.add(current.toString());
        return result;
    }
}
