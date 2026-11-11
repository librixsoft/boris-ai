package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HintBar extends Label {

    private static final String HINT_SEPARATOR = "  ·  ";
    private static final String BASE_HINTS = "/ comandos" + HINT_SEPARATOR + "ESC: abortar" + HINT_SEPARATOR + "↑↓: historial" + HINT_SEPARATOR + "^N: nueva línea";

    private boolean toolDetailsVisible = false;
    private int hiddenCount = 0;

    public HintBar() {
        super(BASE_HINTS + HINT_SEPARATOR + "^G: mostrar detalles");
        setForegroundColor(UiTheme.MUTED);
    }

    public void setToolDetailsVisible(boolean visible) {
        this.toolDetailsVisible = visible;
        if (visible) {
            this.hiddenCount = 0;
        }
        updateText();
    }

    public void showHiddenDetailsHint(int count) {
        this.hiddenCount = count;
        updateText();
    }

    private void updateText() {
        StringBuilder sb = new StringBuilder(BASE_HINTS);
        if (toolDetailsVisible) {
            sb.append(HINT_SEPARATOR).append("^G: ocultar detalles");
        } else {
            sb.append(HINT_SEPARATOR).append("^G: mostrar detalles");
            if (hiddenCount > 0) {
                sb.append("  [").append(hiddenCount).append(" ocultos]");
            }
        }
        setText(sb.toString());
    }
}
