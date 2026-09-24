package com.mk.movieticketbooking.common.exception;

import com.mk.movieticketbooking.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Central exception -> HTTP response translator.
 * <p>
 * Every unhandled exception hitting a controller flows through here, producing
 * a consistent {@link ApiError} JSON body. Business errors get their intended
 * status; unexpected errors are logged with a trace id and returned as a
 * generic 500 (never leaking internals to the client).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, null);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
    return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req, null);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req, null);
  }

  /**
   * Bean-validation failures on @RequestBody / @Valid. Produces per-field
   * error breakdown so clients can highlight the offending inputs.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
        .toList();
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
        "Request contains invalid fields", req, fieldErrors);
  }

  /** Bean-validation failures on @PathVariable / @RequestParam / method params. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
        .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
        .toList();
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
        "Request contains invalid parameters", req, fieldErrors);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
        "Authentication required", req, null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest req) {
    return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
        "You do not have permission to access this resource", req, null);
  }

  /**
   * Catch-all. Real cause logged with the trace id; client sees only a
   * generic message so internals don't leak.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    String traceId = UUID.randomUUID().toString();
    log.error("Unhandled exception [traceId={}] at {} {}", traceId,
        req.getMethod(), req.getRequestURI(), ex);
    ApiError body = new ApiError(
        Instant.now(),
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "INTERNAL_ERROR",
        "An unexpected error occurred. Reference: " + traceId,
        req.getRequestURI(),
        traceId,
        null);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private ResponseEntity<ApiError> build(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest req,
      List<ApiError.FieldError> fieldErrors) {
    ApiError body = new ApiError(
        Instant.now(),
        status.value(),
        code,
        message,
        req.getRequestURI(),
        UUID.randomUUID().toString(),
        fieldErrors);
    return ResponseEntity.status(status).body(body);
  }
}
