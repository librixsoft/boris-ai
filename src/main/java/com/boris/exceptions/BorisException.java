package com.boris.exceptions;

public class BorisException extends RuntimeException {

    public BorisException(String message) {
        super(message);
    }

    public BorisException(String message, Throwable cause) {
        super(message, cause);
    }
}
