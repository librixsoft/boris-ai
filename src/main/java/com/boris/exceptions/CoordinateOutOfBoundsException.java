package com.boris.exceptions;

public class CoordinateOutOfBoundsException extends BorisException {

    public CoordinateOutOfBoundsException(String message) {
        super(message);
    }

    public CoordinateOutOfBoundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
