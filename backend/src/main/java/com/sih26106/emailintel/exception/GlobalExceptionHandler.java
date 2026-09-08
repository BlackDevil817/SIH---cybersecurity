package com.sih26106.emailintel.exception;

import com.sih26106.emailintel.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for the whole module.
 *
 * Design intent: individual malformed headers/hops must NEVER surface as HTTP errors -
 * those are captured as warnings inside the domain objects. This handler only deals with
 * request-level / whole-file-level failures (bad upload, unparseable file, missing analysis).
 *
 * Never leaks stack traces to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidEmlException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidEml(InvalidEmlException ex, HttpServletRequest req) {
        log.warn("Invalid upload request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), req);
    }

    @ExceptionHandler(UnsupportedEmailFormatException.class)
    public ResponseEntity<ErrorResponseDto> handleUnsupportedFormat(UnsupportedEmailFormatException ex, HttpServletRequest req) {
        log.warn("Unprocessable email: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_EMAIL", ex.getMessage(), req);
    }

    @ExceptionHandler({FileTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponseDto> handleTooLarge(Exception ex, HttpServletRequest req) {
        log.warn("Upload rejected - file too large");
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Uploaded file exceeds the maximum allowed size.", req);
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(AnalysisNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(HeaderParsingException.class)
    public ResponseEntity<ErrorResponseDto> handleHeaderParsing(HeaderParsingException ex, HttpServletRequest req) {
        // Should rarely reach here - most header parsing failures are absorbed as warnings.
        log.error("Unrecovered header parsing failure: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "HEADER_PARSING_FAILED", ex.getMessage(), req);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDto> handleMultipart(MultipartException ex, HttpServletRequest req) {
        log.warn("Malformed multipart request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_MULTIPART_REQUEST",
                "The multipart/form-data request could not be read.", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                message.isBlank() ? "Request validation failed." : message, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error handling request {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.", req);
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        ErrorResponseDto body = new ErrorResponseDto(
                Instant.now(),
                status.value(),
                error,
                message,
                req.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
