package com.mk.movieticketbooking.common.exception;

/**
 * Thrown when a requested resource does not exist. Maps to HTTP 404.
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }

  public static NotFoundException of(String resource, Object id) {
    return new NotFoundException(resource + " not found: " + id);
  }
}
