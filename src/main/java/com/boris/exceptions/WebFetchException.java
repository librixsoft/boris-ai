package com.boris.exceptions;

public class WebFetchException extends BorisException {

    public WebFetchException(String message) {
        super(message);
    }

    public WebFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
