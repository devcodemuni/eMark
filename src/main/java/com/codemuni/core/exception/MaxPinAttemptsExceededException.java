package com.codemuni.core.exception;

public class MaxPinAttemptsExceededException extends RuntimeException {
    public MaxPinAttemptsExceededException(String message) {
        super(message);
    }

    public MaxPinAttemptsExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}