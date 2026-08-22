package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.Separator;

public final class UiTheme {

    public static final TextColor BG = new TextColor.RGB(28, 30, 38);
    public static final TextColor BG_ELEVATED = new TextColor.RGB(39, 42, 54);
    public static final TextColor FG = new TextColor.RGB(248, 248, 242);
    public static final TextColor MUTED = new TextColor.RGB(98, 114, 164);
    public static final TextColor ACCENT = new TextColor.RGB(189, 147, 249);
    public static final TextColor USERC = new TextColor.RGB(255, 184, 108);
    public static final TextColor SELECTED_BG = new TextColor.RGB(58, 61, 79);

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
