package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.Separator;

public final class UiTheme {

    public static final TextColor BG = new TextColor.RGB(10, 8, 2);
    public static final TextColor BG_ELEVATED = new TextColor.RGB(65, 52, 28);
    public static final TextColor FG = new TextColor.RGB(205, 170, 90);
    public static final TextColor MUTED = new TextColor.RGB(140, 115, 65);
    public static final TextColor ACCENT = new TextColor.RGB(0, 210, 0);
    public static final TextColor USERC = new TextColor.RGB(205, 170, 90);
    public static final TextColor SELECTED_BG = new TextColor.RGB(45, 36, 20);
    public static final TextColor SELECT_BG = new TextColor.RGB(65, 52, 28);
    public static final TextColor SELECT_FG = new TextColor.RGB(220, 182, 100);
    public static final TextColor SELECT_HIGHLIGHT_BG = new TextColor.RGB(0, 210, 0);
    public static final TextColor SELECT_HIGHLIGHT_FG = new TextColor.RGB(0, 0, 0);
    public static final TextColor THINKING = new TextColor.RGB(180, 180, 180);
    public static final TextColor THINKING_BOLD = new TextColor.RGB(210, 210, 210);
    public static final TextColor THINKING_MUTED = new TextColor.RGB(140, 140, 140);

    private UiTheme() {
    }

    public static SimpleTheme darkTheme() {
        SimpleTheme theme = SimpleTheme.makeTheme(
                false,
                FG,
                BG,
                FG,
                BG_ELEVATED,
                ACCENT,
                SELECTED_BG,
                BG
        );
        theme.addOverride(Separator.class, MUTED, BG);
        return theme;
    }
}
