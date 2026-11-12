package com.boris.exceptions;

/**
 * Excepción lanzada cuando se detecta un bucle de ejecución de herramientas
 * repetitivas o sin progreso informativo, para romper el bucle del agente.
 */
public class ToolLoopException extends BorisException {

    public ToolLoopException(String message) {
        super(message);
    }
}
