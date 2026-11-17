package com.boris.exceptions;

/**
 * Excepción lanzada cuando una acción de browser excede el timeout de espera
 * a pantalla estable o a carga de página.
 */
public class BrowserActionTimeoutException extends BorisException {

    public BrowserActionTimeoutException(String message) {
        super(message);
    }

    public BrowserActionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
