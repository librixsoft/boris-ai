package com.boris.exceptions;

public class SessionLogException extends BorisException {

    public SessionLogException(String message) {
        super(message);
    }

    public SessionLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
