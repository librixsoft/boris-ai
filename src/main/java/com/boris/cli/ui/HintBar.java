package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class HintBar extends Label {

    public HintBar() {
        super(" /exit salir   /clear limpiar   ESC: abortar tarea   Tab: cambiar foco   ↑↓: historial (input) / scroll (chat)   PgUp/PgDn: scroll del chat");
        setForegroundColor(UiTheme.ACCENT);
    }
}
