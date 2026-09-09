package com.sih26106.emailintel.exception;

/**
 * Thrown when the upload itself is invalid at a basic level:
 * no file provided, empty file, unreadable multipart payload, etc.
 * Maps to HTTP 400.
 */
public class InvalidEmlException extends RuntimeException {
    public InvalidEmlException(String message) {
        super(message);
    }

    public InvalidEmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
