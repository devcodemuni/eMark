package com.codemuni.core.exception;

public class ServerStartupException extends RuntimeException {
    public ServerStartupException() {
        super("Server startup failed");
    }

    public ServerStartupException(String message) {
        super(message);
    }

    public ServerStartupException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerStartupException(Throwable cause) {
        super(cause);
    }

}