package com.mk.movieticketbooking.booking;

/**
 * Booking lifecycle.
 * <pre>
 *   PENDING ── confirm ──> CONFIRMED
 *      │
 *      ├── expire ──> EXPIRED   (hold TTL elapsed without payment)
 *      └── cancel ──> CANCELLED (customer or admin explicit cancel)
 * </pre>
 */
public enum BookingStatus {
  PENDING,
  CONFIRMED,
  EXPIRED,
  CANCELLED
}
