package com.mk.movieticketbooking.notification;

import com.mk.movieticketbooking.booking.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fires pre-show reminders for CONFIRMED bookings whose show starts
 * inside the configured lookahead window. Mirrors the two-step outer /
 * inner pattern used by {@code HoldSweeper}: a read-only query gathers
 * candidate ids, then each is processed in its own REQUIRES_NEW
 * transaction so one bad row cannot fail the whole batch.
 * <p>
 * Idempotency is guaranteed by the {@code remindedAt} column on the
 * booking: {@link BookingService#remindOne} refuses to remind a booking
 * that is already stamped.
 */
@Component
public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final BookingService bookings;
  private final NotificationProperties props;

  public ReminderScheduler(BookingService bookings, NotificationProperties props) {
    this.bookings = bookings;
    this.props = props;
  }

  @Scheduled(fixedDelayString = "${app.notification.reminder-sweep-interval-ms}")
  public void sweep() {
    Instant now = Instant.now();
    Instant until = now.plus(props.reminderLookahead());
    List<UUID> candidates = bookings.findRemindableBookingIds(now, until);
    if (candidates.isEmpty()) return;
    int reminded = 0;
    for (UUID id : candidates) {
      if (bookings.remindOne(id)) reminded++;
    }
    log.info("Reminder sweep: {} candidates, {} reminded", candidates.size(), reminded);
  }
}
