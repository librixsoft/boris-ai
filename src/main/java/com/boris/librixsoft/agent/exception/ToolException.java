package com.boris.librixsoft.agent.exception;

/**
 * Excepción personalizada para las herramientas del agente
 */
public class ToolException extends RuntimeException {
    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}