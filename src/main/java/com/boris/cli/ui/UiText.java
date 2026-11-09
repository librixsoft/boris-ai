package com.boris.cli.ui;

public final class UiText {

    private static final char ANSI_ESCAPE = '\u001b';

    private UiText() {
    }

    public static String requireNoAnsiEscape(String value, String parameterName) {
        if (value.indexOf(ANSI_ESCAPE) >= 0) {
            throw new AnsiEscapeInUiTextException(parameterName);
        }
        return value;
    }
}
