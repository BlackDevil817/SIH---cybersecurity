package com.sih26106.emailintel.exception;

public class EmailParsingException extends RuntimeException {
    public EmailParsingException(String message) { super(message); }
    public EmailParsingException(String message, Throwable cause) { super(message, cause); }
}