package com.boris.exceptions;

public class BrowserTabNotFoundException extends BorisException {

    public BrowserTabNotFoundException(String message) {
        super(message);
    }

    public BrowserTabNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
