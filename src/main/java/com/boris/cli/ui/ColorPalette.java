package com.boris.cli.ui;

import org.jline.utils.AttributedStyle;

/**
 * Color palette for the Boris UI theme using JLine3 AttributedStyle.
 * Provides a single accent color with neutral tones for all other elements.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All color operations MUST use JLine3 AttributedStyle or the provided String methods.
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
 */
public class ColorPalette {
    
    private final int[] accent;
    private final int[] dim;
    private final int[] fg;
    private final int[] warn;
    private final int[] user;
    
    public ColorPalette(int[] accent, int[] dim, int[] fg, int[] warn, int[] user) {
        this.accent = accent;
        this.dim = dim;
        this.fg = fg;
        this.warn = warn;
        this.user = user;
    }
    
    /**
     * Default Boris color palette.
     */
    public static ColorPalette defaultPalette() {
        return new ColorPalette(
            new int[] { 209, 122, 92 },   // accent: prompt glyph / brand dot
            new int[] { 118, 118, 124 },  // dim: secondary / help text
            new int[] { 225, 225, 228 },  // fg: response text
            new int[] { 209, 160, 100 },  // warn: aborted
            new int[] { 140, 180, 220 }   // user: user question text
        );
    }
    
    public AttributedStyle accent() {
        return AttributedStyle.DEFAULT.foreground(accent[0], accent[1], accent[2]);
    }
    
    public String accentStr() {
        return rgb(accent);
    }
    
    public AttributedStyle dim() {
        return AttributedStyle.DEFAULT.foreground(dim[0], dim[1], dim[2]);
    }
    
    public String dimStr() {
        return rgb(dim);
    }
    
    public AttributedStyle fg() {
        return AttributedStyle.DEFAULT.foreground(fg[0], fg[1], fg[2]);
    }
    
    public String fgStr() {
        return rgb(fg);
    }
    
    public AttributedStyle warn() {
        return AttributedStyle.DEFAULT.foreground(warn[0], warn[1], warn[2]);
    }
    
    public String warnStr() {
        return rgb(warn);
    }
    
    public AttributedStyle reset() {
        return AttributedStyle.DEFAULT;
    }
    
    public String resetStr() {
        return "\033[0m";
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
    
    public AttributedStyle user() {
        return AttributedStyle.DEFAULT.foreground(user[0], user[1], user[2]);
    }
    
    public String userStr() {
        return rgb(user);
    }
    
    public int[] getUser() {
        return user;
    }
    
    private static String rgb(int[] c) {
        return String.format("\033[38;2;%d;%d;%dm", c[0], c[1], c[2]);
    }
}
