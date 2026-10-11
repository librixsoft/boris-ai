package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.Separator;

public final class UiTheme {

    public static final TextColor BG = new TextColor.RGB(0, 0, 0);
    public static final TextColor BG_ELEVATED = new TextColor.RGB(50, 40, 22);
    public static final TextColor FG = new TextColor.RGB(175, 145, 75);
    public static final TextColor MUTED = new TextColor.RGB(115, 95, 55);
    public static final TextColor ACCENT = new TextColor.RGB(0, 210, 0);
    public static final TextColor USERC = new TextColor.RGB(175, 145, 75);
    public static final TextColor SELECTED_BG = new TextColor.RGB(35, 28, 15);
    public static final TextColor SELECT_BG = new TextColor.RGB(50, 40, 22);
    public static final TextColor SELECT_FG = new TextColor.RGB(190, 155, 85);
    public static final TextColor SELECT_HIGHLIGHT_BG = new TextColor.RGB(0, 210, 0);
    public static final TextColor SELECT_HIGHLIGHT_FG = new TextColor.RGB(0, 0, 0);
    public static final TextColor THINKING = new TextColor.RGB(180, 180, 180);
    public static final TextColor THINKING_BOLD = new TextColor.RGB(210, 210, 210);
    public static final TextColor THINKING_MUTED = new TextColor.RGB(140, 140, 140);
    public static final TextColor DIFF_ADD = new TextColor.RGB(80, 220, 80);
    public static final TextColor DIFF_REMOVE = new TextColor.RGB(220, 80, 80);
    public static final TextColor DIFF_ADD_BG = new TextColor.RGB(20, 50, 20);
    public static final TextColor DIFF_REMOVE_BG = new TextColor.RGB(50, 20, 20);
    public static final TextColor LINE_NUMBER = new TextColor.RGB(100, 100, 100);
    public static final TextColor DIFF_HEADER = new TextColor.RGB(130, 180, 220);
    public static final TextColor DIFF_BORDER = new TextColor.RGB(70, 70, 70);

    // Syntax highlighting colors for code in diffs
    public static final TextColor SYNTAX_KEYWORD = new TextColor.RGB(225, 140, 255);
    public static final TextColor SYNTAX_TYPE = new TextColor.RGB(90, 225, 230);
    public static final TextColor SYNTAX_STRING = new TextColor.RGB(255, 220, 115);
    public static final TextColor SYNTAX_NUMBER = new TextColor.RGB(255, 165, 90);
    public static final TextColor SYNTAX_COMMENT = new TextColor.RGB(155, 175, 185);
    public static final TextColor SYNTAX_ANNOTATION = new TextColor.RGB(255, 210, 70);
    public static final TextColor SYNTAX_FUNCTION = new TextColor.RGB(125, 195, 255);
    public static final TextColor SYNTAX_OPERATOR = new TextColor.RGB(215, 220, 230);
    public static final TextColor SYNTAX_BOOLEAN = new TextColor.RGB(255, 130, 160);
    public static final TextColor SYNTAX_TEXT = new TextColor.RGB(240, 240, 240);

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
