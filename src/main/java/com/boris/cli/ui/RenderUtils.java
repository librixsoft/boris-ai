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
}
