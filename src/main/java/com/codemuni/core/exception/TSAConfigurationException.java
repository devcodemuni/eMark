package com.codemuni.core.exception;

/**
 * Thrown to indicate that a TSA URL is invalid (null, empty, or incorrectly formatted).
 */
public class TSAConfigurationException extends RuntimeException {

    public TSAConfigurationException(String message) {
        super(message);
    }

    public TSAConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}