package com.boris.exceptions;

public class WebSearchException extends BorisException {

    public WebSearchException(String message) {
        super(message);
    }

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
