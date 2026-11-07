package com.boris.exceptions;

public class VersionException extends BorisException {

    public VersionException(String message) {
        super(message);
    }

    public VersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
