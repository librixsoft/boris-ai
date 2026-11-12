package com.boris.exceptions;

public class WebSearchBlockedException extends WebSearchException {

    public WebSearchBlockedException(String message) {
        super(message);
    }

    public WebSearchBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
