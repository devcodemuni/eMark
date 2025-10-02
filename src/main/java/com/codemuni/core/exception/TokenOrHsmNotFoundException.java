package com.codemuni.core.exception;

/**
 * Exception thrown when a PKCS#11 token with the specified serial number is not found.
 */
public class TokenOrHsmNotFoundException extends RuntimeException {

    /**
     * Constructs a new TokenNotFoundException with the specified detail message.
     *
     * @param message The detail message explaining the reason for the exception.
     */
    public TokenOrHsmNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new TokenNotFoundException with the specified detail message and cause.
     *
     * @param message The detail message explaining the reason for the exception.
     * @param cause The cause of the exception (a throwable that caused this exception to be thrown).
     */
    public TokenOrHsmNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}