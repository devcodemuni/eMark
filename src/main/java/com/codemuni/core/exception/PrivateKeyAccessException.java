package com.codemuni.core.exception;


public class PrivateKeyAccessException extends Exception {

    // Constructor that accepts a message
    public PrivateKeyAccessException(String message) {
        super(message);
    }

    // Constructor that accepts a message and a cause
    public PrivateKeyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}