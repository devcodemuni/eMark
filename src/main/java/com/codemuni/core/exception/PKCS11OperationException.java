package com.codemuni.core.exception;

public class PKCS11OperationException extends RuntimeException {
    public PKCS11OperationException(String message) {
        super(message);
    }
    public PKCS11OperationException(String message, Throwable cause) {
        super(message, cause);
    }
}