package com.boris.librixsoft.exception;

public class LlamaModelException extends RuntimeException {
    public LlamaModelException(String message) {
        super(message);
    }

    public LlamaModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
