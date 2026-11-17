package com.boris.exceptions;

/**
 * Excepción lanzada cuando no es posible abrir o cerrar la ventana de Chromium
 * gestionada por Playwright.
 */
public class BrowserLaunchFailedException extends BorisException {

    public BrowserLaunchFailedException(String message) {
        super(message);
    }

    public BrowserLaunchFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
