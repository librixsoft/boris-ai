package com.boris.context;

import com.boris.exceptions.BorisException;

/**
 * Excepción lanzada por {@link ExplorationContextWriter} cuando no puede
 * escribir o leer el registro de exploración (p. ej. sin project path o
 * sin sessionId seteado).
 */
public class ExplorationContextWriterException extends BorisException {

    public ExplorationContextWriterException(String message) {
        super(message);
    }

    public ExplorationContextWriterException(String message, Throwable cause) {
        super(message, cause);
    }
}
