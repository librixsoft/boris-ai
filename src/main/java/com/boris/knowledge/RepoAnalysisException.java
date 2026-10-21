package com.boris.knowledge;

public class RepoAnalysisException extends RuntimeException {

    public RepoAnalysisException(String message) {
        super(message);
    }

    public RepoAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
