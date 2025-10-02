package com.codemuni.core.exception;


public class NotADigitalSignatureException extends Exception {
    public NotADigitalSignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotADigitalSignatureException(String message) {
        super(message);
    }

    public NotADigitalSignatureException(Throwable cause) {
        super(cause);
    }
}