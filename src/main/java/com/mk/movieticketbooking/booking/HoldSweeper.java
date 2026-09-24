package com.mk.movieticketbooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Periodic sweeper that releases seats held by PENDING bookings whose
 * TTL has elapsed. Rate governed by {@code app.booking.hold-sweep-interval-ms}.
 * <p>
 * The find and the per-booking expire run in separate transactions so a
 * single conflict (typically a concurrent confirm winning the race)
 * doesn't roll back the entire sweep.
 */
@Component
public class HoldSweeper {

  private static final Logger log = LoggerFactory.getLogger(HoldSweeper.class);

  private final BookingService bookings;

  public HoldSweeper(BookingService bookings) {
    this.bookings = bookings;
  }

  @Scheduled(fixedDelayString = "${app.booking.hold-sweep-interval-ms}")
  public void sweep() {
    Instant cutoff = Instant.now();
    List<UUID> expired = bookings.findExpiredHoldIds(cutoff);
    if (expired.isEmpty()) {
      return;
    }
    int released = 0;
    for (UUID id : expired) {
      if (bookings.expireOne(id)) {
        released++;
      }
    }
    log.info("Hold sweep: {} candidates, {} expired", expired.size(), released);
  }
}
