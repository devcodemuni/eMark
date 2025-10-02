package com.codemuni.core.exception;

import java.io.IOException;

public class UserCancelledPasswordEntryException extends IOException {
    public UserCancelledPasswordEntryException(String message, Throwable cause) {
        super(message, cause);
    }
    public UserCancelledPasswordEntryException(String message) {
        super(message);
    }
}