package com.boris.exceptions;

public class EmergencyFailsafeTriggeredException extends BorisException {

    public EmergencyFailsafeTriggeredException(String message) {
        super(message);
    }

    public EmergencyFailsafeTriggeredException(String message, Throwable cause) {
        super(message, cause);
    }
}
