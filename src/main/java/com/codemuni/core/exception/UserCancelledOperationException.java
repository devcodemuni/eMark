package com.codemuni.core.exception;

public class UserCancelledOperationException extends RuntimeException {
    public UserCancelledOperationException(String message) {
        super(message);
    }

    public UserCancelledOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}