package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HeaderBar extends Label {

    private static final String VERSION = "2.2.0";

    public HeaderBar(String modelName) {
        super(render(modelName));
        setForegroundColor(UiTheme.ACCENT);
    }

    private static final int MAX_NAME_LENGTH = 22;

    private static String compactName(String name) {
        String clean = name != null && !name.isBlank() ? name.strip() : null;
        if (clean == null) {
            return null;
        }
        if (clean.endsWith(":latest")) {
            clean = clean.substring(0, clean.length() - ":latest".length());
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            return clean.substring(0, MAX_NAME_LENGTH - 1) + "…";
        }
        return clean;
    }

    private static String render(String modelName) {
        String main = compactName(modelName);
        if (main == null) {
            main = "unknown";
        }
        return " boris  ·  " + main + "  [v" + VERSION + "]";
    }

    public void updateModel(String modelName) {
        setText(render(modelName));
        invalidate();
    }
}
