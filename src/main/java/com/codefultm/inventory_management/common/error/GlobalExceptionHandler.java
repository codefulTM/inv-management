package com.codefultm.inventory_management.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProblemDetail(
            String status,
            int code,
            String error,
            String message,
            String path,
            // Serialized as ISO-8601 by the global Jackson config
            // (spring.jackson.serialization.write-dates-as-timestamps = false).
            // Do NOT put @JsonFormat(pattern = ...) on an Instant here: Jackson's
            // Instant serializer cannot apply a bare pattern without a zone and
            // emits broken JSON for it.
            Instant timestamp,
            Map<String, String> errors
    ) {
        public ProblemDetail(HttpStatus status, String message, String path) {
            this(status.name(), status.value(), status.getReasonPhrase(), message, path, Instant.now(), null);
        }

        public ProblemDetail(HttpStatus status, String message, String path, Map<String, String> errors) {
            this(status.name(), status.value(), status.getReasonPhrase(), message, path, Instant.now(), errors);
        }
    }

    /**
     * Bean-validation failures on a {@code @Valid} request body.
     * Produces a 400 whose {@code errors} map is {@code field → message}, so a client can
     * highlight the offending inputs instead of parsing a single prose message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "invalid value";
            errors.merge(fieldError.getField(), message, (first, second) -> first + "; " + second);
        }
        ProblemDetail body = new ProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed body (bad JSON, wrong enum, …) → 400 instead of a generic 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.BAD_REQUEST, "Malformed request body", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /** Query/path parameter of the wrong type (e.g. {@code page=abc}) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, jakarta.servlet.http.HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        ProblemDetail body = new ProblemDetail(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * {@code ?sort=notAColumn} — Spring Data cannot build the {@code Sort} → 400.
     * Without this it would surface as an unhelpful 500.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handleUnknownSortProperty(PropertyReferenceException ex, jakarta.servlet.http.HttpServletRequest request) {
        String message = "Unknown sort property '" + ex.getPropertyName() + "'";
        ProblemDetail body = new ProblemDetail(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail body = new ProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
