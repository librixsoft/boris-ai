package com.boris.exceptions;

/**
 * Excepción lanzada cuando el comando OCR nativo no se encuentra,
 * falla al ejecutarse o devuelve una salida inválida.
 */
public class OcrExecutionException extends BorisException {

    public OcrExecutionException(String message) {
        super(message);
    }

    public OcrExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
