package com.boris.librixsoft.exception;

/**
 * Thrown when a GGUF model does not contain the required chat template metadata.
 * This is a configuration error indicating that stop‑token extraction cannot be performed.
 */
public class MissingChatTemplateException extends RuntimeException {
    public MissingChatTemplateException() {
        super();
    }
    public MissingChatTemplateException(String message) {
        super(message);
    }
    public MissingChatTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
    public MissingChatTemplateException(Throwable cause) {
        super(cause);
    }
}
