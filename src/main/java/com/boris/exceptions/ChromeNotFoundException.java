package com.boris.exceptions;

public class ChromeNotFoundException extends WebSearchException {

    public ChromeNotFoundException(String message) {
        super(message);
    }

    public ChromeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
