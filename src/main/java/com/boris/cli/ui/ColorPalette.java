package com.boris.cli.ui;

/**
 * Color palette for the Boris UI theme.
 * Provides a single accent color with neutral tones for all other elements.
 */
public class ColorPalette {
    
    private final int[] accent;
    private final int[] dim;
    private final int[] fg;
    private final int[] warn;
    
    public ColorPalette(int[] accent, int[] dim, int[] fg, int[] warn) {
        this.accent = accent;
        this.dim = dim;
        this.fg = fg;
        this.warn = warn;
    }
    
    /**
     * Default Boris color palette.
     */
    public static ColorPalette defaultPalette() {
        return new ColorPalette(
            new int[] { 209, 122, 92 },   // accent: prompt glyph / brand dot
            new int[] { 118, 118, 124 },  // dim: secondary / help text
            new int[] { 225, 225, 228 },  // fg: response text
            new int[] { 209, 160, 100 }   // warn: aborted
        );
    }
    
    public String accent() {
        return rgb(accent);
    }
    
    public String dim() {
        return rgb(dim);
    }
    
    public String fg() {
        return rgb(fg);
    }
    
    public String warn() {
        return rgb(warn);
    }
    
    public String reset() {
        return "\033[0m";
    }
    
    private static String rgb(int[] c) {
        return String.format("\033[38;2;%d;%d;%dm", c[0], c[1], c[2]);
    }
    
    public int[] getAccent() {
        return accent;
    }
    
    public int[] getDim() {
        return dim;
    }
    
    public int[] getFg() {
        return fg;
    }
    
    public int[] getWarn() {
        return warn;
    }
}
