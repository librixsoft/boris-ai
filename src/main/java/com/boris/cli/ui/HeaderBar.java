package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HeaderBar extends Label {

    private static final String VERSION = "2.2.0";

    public HeaderBar(String modelName) {
        super(render(modelName, null, null));
        setForegroundColor(UiTheme.ACCENT);
    }

    public HeaderBar(String mainModel, String drafterModel, Boolean drafterEnabled) {
        super(render(mainModel, drafterModel, drafterEnabled));
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

    private static String render(String mainModel, String drafterModel, Boolean drafterEnabled) {
        String main = compactName(mainModel);
        if (main == null) {
            main = "unknown";
        }
        String drafter;
        if (drafterEnabled != null && drafterEnabled) {
            String compactDrafter = compactName(drafterModel);
            drafter = compactDrafter != null ? compactDrafter : "sin configurar";
        } else {
            drafter = "off";
        }
        return " boris  ·  " + main + "  ·  drafter: " + drafter + "  [v" + VERSION + "]";
    }

    public void updateModel(String modelName) {
        setText(render(modelName, null, null));
        invalidate();
    }

    public void updateModels(String mainModel, String drafterModel, Boolean drafterEnabled) {
        setText(render(mainModel, drafterModel, drafterEnabled));
        invalidate();
    }
}
