package com.boris.cli.ui;

import com.boris.context.SearchSessionContext;
import com.boris.exceptions.BorisException;
import com.googlecode.lanterna.gui2.Label;

/**
 * Barra lateral derecha del header que muestra el SESSION_ID de la sesión actual.
 * Se construye una vez al iniciar la UI; la sesión ya está seteada por BorisApp.
 */
public class SessionIdBar extends Label {

    public SessionIdBar() {
        super(buildText());
        setForegroundColor(UiTheme.MUTED);
    }

    private static String buildText() {
        String sessionId = SearchSessionContext.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new BorisException("No session ID set for search context");
        }
        return "sesión: " + sessionId;
    }
}
