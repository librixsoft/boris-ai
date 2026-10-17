package com.boris.exceptions;

/**
 * Excepción específica para errores de chat/LLM.
 * Proporciona contexto adicional sobre el tipo de error.
 */
public class ChatException extends BorisException {

    public enum ErrorType {
        CONNECTION_ERROR,      // No se puede conectar al modelo
        TIMEOUT,              // Timeout en la respuesta
        STREAMING_ERROR,      // Error durante streaming
        MODEL_ERROR,          // Error del modelo (respuesta inválida)
        ABORTED,              // Cancelado por el usuario
        INVALID_INPUT,        // Input inválido
        UNKNOWN               // Error desconocido
    }

    private final ErrorType errorType;
    private final String modelName;

    public ChatException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
        this.modelName = null;
    }

    public ChatException(String message, ErrorType errorType, String modelName) {
        super(message);
        this.errorType = errorType;
        this.modelName = modelName;
    }

    public ChatException(String message, Throwable cause, ErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
        this.modelName = null;
    }

    public ChatException(String message, Throwable cause, ErrorType errorType, String modelName) {
        super(message, cause);
        this.errorType = errorType;
        this.modelName = modelName;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDisplayMessage() {
        String base = switch (errorType) {
            case CONNECTION_ERROR -> "No se pudo conectar al modelo";
            case TIMEOUT -> "Timeout esperando respuesta del modelo";
            case STREAMING_ERROR -> "Error durante streaming";
            case MODEL_ERROR -> "Error en la respuesta del modelo";
            case ABORTED -> "Operación cancelada";
            case INVALID_INPUT -> "Input inválido";
            case UNKNOWN -> "Error desconocido";
        };

        if (modelName != null) {
            base += " (" + modelName + ")";
        }

        Throwable cause = getCause();
        if (cause != null && cause.getMessage() != null) {
            base += ": " + cause.getMessage();
        }

        return base;
    }
}
