package com.boris.exceptions;

public class WebSearchLimitException extends WebSearchException {

    public WebSearchLimitException(String message) {
        super(message);
    }

    public WebSearchLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
