package com.sih26106.emailintel.exception;

/**
 * Represents a failure parsing a specific header or header group.
 *
 * IMPORTANT: per the forensic-tolerance requirement, this exception is generally
 * caught internally (e.g. inside HeaderExtractor / ReceivedHeaderAnalyzer) and
 * converted into a warning rather than allowed to propagate and fail the whole
 * analysis. It is only thrown to the caller in situations where the caller has
 * explicitly asked for strict parsing.
 */
public class HeaderParsingException extends RuntimeException {
    public HeaderParsingException(String message) {
        super(message);
    }

    public HeaderParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
