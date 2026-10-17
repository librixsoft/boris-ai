package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HeaderBar extends Label {

    private static final String VERSION = "2.2.0";

    public HeaderBar(String modelName) {
        super(" boris  ·  terminal agent  ·  " + (modelName != null ? modelName : "unknown") + "  [v" + VERSION + "]");
        setForegroundColor(UiTheme.ACCENT);
    }

    public void updateModel(String modelName) {
        setText(" boris  ·  terminal agent  ·  " + (modelName != null && !modelName.isBlank() ? modelName : "unknown") + "  [v" + VERSION + "]");
        invalidate();
    }
}
