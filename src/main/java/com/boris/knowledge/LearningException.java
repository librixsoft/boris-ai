package com.boris.knowledge;

public class LearningException extends RuntimeException {

    public LearningException(String message) {
        super(message);
    }

    public LearningException(String message, Throwable cause) {
        super(message, cause);
    }
}
