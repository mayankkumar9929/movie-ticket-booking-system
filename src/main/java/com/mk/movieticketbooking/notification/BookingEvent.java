package com.mk.movieticketbooking.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events fired at booking state transitions. Published inside
 * the booking transaction and delivered to listeners AFTER_COMMIT so a
 * rolled-back transition never notifies.
 */
public sealed interface BookingEvent {

  UUID bookingId();

  UUID userId();

  record BookingConfirmed(
      UUID bookingId,
      UUID userId,
      UUID showId,
      BigDecimal amountPaid,
      String discountCode)
      implements BookingEvent {}

  record BookingCancelled(
      UUID bookingId,
      UUID userId,
      UUID showId,
      BigDecimal refundAmount)
      implements BookingEvent {}

  record BookingExpired(
      UUID bookingId,
      UUID userId,
      UUID showId)
      implements BookingEvent {}

  /**
   * Pre-show reminder for a CONFIRMED booking. Fired once per booking by
   * the reminder scheduler when the show is inside the configured
   * lookahead window.
   */
  record BookingReminder(
      UUID bookingId,
      UUID userId,
      UUID showId,
      Instant startsAt)
      implements BookingEvent {}
}
