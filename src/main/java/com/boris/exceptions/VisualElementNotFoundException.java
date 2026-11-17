package com.boris.exceptions;

public class VisualElementNotFoundException extends BorisException {

    public VisualElementNotFoundException(String message) {
        super(message);
    }

    public VisualElementNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
