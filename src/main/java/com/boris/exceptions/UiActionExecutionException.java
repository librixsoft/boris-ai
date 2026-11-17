package com.boris.exceptions;

public class UiActionExecutionException extends BorisException {

    public UiActionExecutionException(String message) {
        super(message);
    }

    public UiActionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
