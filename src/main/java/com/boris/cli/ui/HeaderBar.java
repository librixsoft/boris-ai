package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HeaderBar extends Label {

    public HeaderBar(String modelName) {
        super(" boris  ·  terminal agent  ·  " + (modelName != null ? modelName : "unknown"));
        setForegroundColor(UiTheme.ACCENT);
    }
}
