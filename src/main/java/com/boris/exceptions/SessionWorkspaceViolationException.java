package com.boris.exceptions;

public class SessionWorkspaceViolationException extends BorisException {

    public SessionWorkspaceViolationException(String message) {
        super(message);
    }

    public SessionWorkspaceViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
