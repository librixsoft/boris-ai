package com.boris.exceptions;

/**
 * Excepción lanzada cuando los argumentos de una tool son inválidos
 * (valor no permitido, clave desconocida, formato incorrecto).
 */
public class InvalidToolArgumentException extends BorisException {

    public InvalidToolArgumentException(String message) {
        super(message);
    }

    public InvalidToolArgumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
