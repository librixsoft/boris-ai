package com.boris.exceptions;

public class WebSearchFallbackException extends BorisException {

    public WebSearchFallbackException(String message) {
        super(message);
    }

    public WebSearchFallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
