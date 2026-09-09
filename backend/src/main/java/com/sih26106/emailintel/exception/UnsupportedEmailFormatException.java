package com.sih26106.emailintel.exception;

/**
 * Thrown when the file was readable as a stream but could not be interpreted as an
 * RFC 5322 / RFC 822 email at all (e.g. binary garbage, wrong file type).
 * Maps to HTTP 422 - the request was well-formed but the email content is unprocessable.
 */
public class UnsupportedEmailFormatException extends RuntimeException {
    public UnsupportedEmailFormatException(String message) {
        super(message);
    }

    public UnsupportedEmailFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
