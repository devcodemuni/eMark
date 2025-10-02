package com.codemuni.core.exception;

public class IncorrectPINException extends Exception {
    public IncorrectPINException(String message, Throwable cause) {
        super(message, cause);
    }

    public IncorrectPINException(String message) {
        super(message);
    }
}