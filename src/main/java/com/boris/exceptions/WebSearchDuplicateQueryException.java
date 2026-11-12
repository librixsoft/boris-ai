package com.boris.exceptions;

public class WebSearchDuplicateQueryException extends WebSearchException {

    public WebSearchDuplicateQueryException(String message) {
        super(message);
    }

    public WebSearchDuplicateQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
