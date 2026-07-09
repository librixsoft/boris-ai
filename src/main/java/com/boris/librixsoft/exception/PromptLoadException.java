package com.boris.librixsoft.exception;

public class PromptLoadException extends RuntimeException {
    public PromptLoadException(String message) {
        super(message);
    }

    public PromptLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
