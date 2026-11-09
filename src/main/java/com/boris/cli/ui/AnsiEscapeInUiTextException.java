package com.boris.cli.ui;

public class AnsiEscapeInUiTextException extends RuntimeException {

    private final String parameterName;

    public AnsiEscapeInUiTextException(String parameterName) {
        super("El texto de la interfaz no puede contener secuencias de escape ANSI, parametro: " + parameterName);
        this.parameterName = parameterName;
    }

    public String parameterName() {
        return parameterName;
    }
}
