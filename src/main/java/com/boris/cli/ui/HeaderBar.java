package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HeaderBar extends Label {

    public HeaderBar() {
        super(" boris  ·  terminal agent");
        setForegroundColor(UiTheme.ACCENT);
    }
}
