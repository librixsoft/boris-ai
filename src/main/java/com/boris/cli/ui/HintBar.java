package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HintBar extends Label {

    private static final String DEFAULT_HINTS = " / comandos   ESC: abortar   ↑↓: historial";

    public HintBar() {
        super(DEFAULT_HINTS);
        setForegroundColor(UiTheme.MUTED);
    }
}
