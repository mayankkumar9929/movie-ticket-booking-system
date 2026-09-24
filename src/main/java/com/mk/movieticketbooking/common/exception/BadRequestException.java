package com.mk.movieticketbooking.common.exception;

/**
 * Thrown for semantically invalid input that passes bean validation
 * but violates a business rule (e.g. show ends before it starts).
 * Maps to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
