package com.boris.cli.ui;

public final class RenderUtils {

    public static final int SCROLLBAR_WIDTH = 2;

    private RenderUtils() {
    }

    public static String padOrTruncate(String line, int width) {
        if (width <= 0) {
            return "";
        }
        if (line.length() >= width) {
            return line.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(line);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String padOrTruncateByDisplayWidth(String line, int displayWidth) {
        if (displayWidth <= 0) {
            return "";
        }
        if (line == null || line.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < displayWidth; j++) sb.append(' ');
            return sb.toString();
        }

        int currentWidth = 0;
        int endIndex = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int w = getDisplayWidth(cp);
            if (currentWidth + w > displayWidth) {
                break;
            }
            currentWidth += w;
            i += (cp > 0xFFFF ? 2 : 1);
            endIndex = i;
            if (currentWidth >= displayWidth) {
                break;
            }
        }

        String truncated = line.substring(0, endIndex);
        if (truncated.length() >= displayWidth) {
            return truncated;
        }
        int pad = displayWidth - truncated.length();
        StringBuilder sb = new StringBuilder(truncated);
        for (int j = 0; j < pad; j++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static int getDisplayWidth(int codePoint) {
        if (codePoint > 0xFFFF) {
            // Zero-width supplementary characters (tags U+E0000-U+E001F)
            if (codePoint >= 0xE0000 && codePoint <= 0xE001F) {
                return 0;
            }
            // Supplementary characters: emojis and most symbols are double-width
            return 2;
        }
        return com.googlecode.lanterna.TerminalTextUtils.isCharDoubleWidth((char) codePoint) ? 2 : 1;
    }

    public static String truncateWithEllipsis(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (text.length() <= maxWidth) {
            return text;
        }
        if (maxWidth <= 3) {
            return text.substring(0, maxWidth);
        }
        return text.substring(0, maxWidth - 3) + "...";
    }
}
