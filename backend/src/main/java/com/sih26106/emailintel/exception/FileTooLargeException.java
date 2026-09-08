package com.sih26106.emailintel.exception;

/**
 * Thrown when an uploaded file exceeds the configured size limit.
 * Maps to HTTP 413.
 */
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(String message) {
        super(message);
    }
}
