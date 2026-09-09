package com.sih26106.emailintel.exception;

public class GeolocationException extends RuntimeException {
    public GeolocationException(String message) { super(message); }
    public GeolocationException(String message, Throwable cause) { super(message, cause); }
}