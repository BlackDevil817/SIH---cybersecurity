package com.sih26106.emailintel.dto;

import java.time.Instant;

/**
 * Standard error envelope returned by the GlobalExceptionHandler for every failure case.
 * Kept as a plain immutable record - simple, serializes cleanly via Jackson.
 */
public record ErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
