package com.cbclean.report.presentation;

import com.cbclean.report.application.report.get.ReportNotFoundException;
import com.cbclean.report.domain.model.InvalidReportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Centralized REST error handling. Produces a consistent JSON error
 * structure and never leaks stack traces or internal details.
 */
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    public record ApiErrorResponse(
            int status,
            String error,
            String message,
            List<Map<String, String>> fieldErrors,
            Instant timestamp) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage()))
                .toList();
        return badRequest("Request validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest("Malformed request body", null);
    }

    @ExceptionHandler(InvalidReportException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidReport(InvalidReportException ex) {
        return badRequest(ex.getMessage(), null);
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ReportNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        ex.getMessage(),
                        null,
                        Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error",
                        "An unexpected error occurred",
                        null,
                        Instant.now()));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message, List<Map<String, String>> fieldErrors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        message,
                        fieldErrors,
                        Instant.now()));
    }
}
