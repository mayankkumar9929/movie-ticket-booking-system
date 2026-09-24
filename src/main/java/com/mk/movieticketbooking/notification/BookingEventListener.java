package com.mk.movieticketbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to {@link BookingEvent}s and hands them off to the configured
 * {@link NotificationSender}. Runs on the {@code notificationExecutor}
 * pool AFTER_COMMIT so a rolled-back booking transition never notifies,
 * and slow sends never block the request thread.
 */
@Component
public class BookingEventListener {

  private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

  private final NotificationSender sender;

  public BookingEventListener(NotificationSender sender) {
    this.sender = sender;
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBookingEvent(BookingEvent event) {
    try {
      switch (event) {
        case BookingEvent.BookingConfirmed e -> sender.send(
            e.userId(),
            "Booking confirmed",
            "Your booking " + e.bookingId() + " is confirmed. Amount paid: "
                + e.amountPaid()
                + (e.discountCode() != null ? " (code " + e.discountCode() + ")" : ""));
        case BookingEvent.BookingCancelled e -> sender.send(
            e.userId(),
            "Booking cancelled",
            "Your booking " + e.bookingId() + " has been cancelled. Refund: "
                + e.refundAmount());
        case BookingEvent.BookingExpired e -> sender.send(
            e.userId(),
            "Booking hold expired",
            "Your seats for booking " + e.bookingId()
                + " were released because payment wasn't completed in time.");
        case BookingEvent.BookingReminder e -> sender.send(
            e.userId(),
            "Show reminder",
            "Reminder: your booking " + e.bookingId()
                + " is for a show starting at " + e.startsAt() + ".");
      }
    } catch (RuntimeException ex) {
      // AFTER_COMMIT: the DB change is already durable. A send failure
      // must not propagate up; log and drop so the pool stays healthy.
      log.warn("Notification send failed for booking {}: {}",
          event.bookingId(), ex.getMessage());
    }
  }
}
