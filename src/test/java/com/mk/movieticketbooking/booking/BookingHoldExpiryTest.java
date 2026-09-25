package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowSeatStatus;
import com.mk.movieticketbooking.support.IntegrationTest;
import com.mk.movieticketbooking.support.TestFixtures;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code HoldSweeper} releases expired PENDING holds and
 * that the same seats can then be held again. Runs with a very short
 * hold TTL and sweep interval so the whole test finishes in seconds.
 */
@IntegrationTest
@TestPropertySource(properties = {
    "app.booking.hold-ttl-minutes=0",           // TTL of zero → hold expires immediately
    "app.booking.hold-sweep-interval-ms=200"
})
class BookingHoldExpiryTest {

  @Autowired BookingService bookings;
  @Autowired BookingRepository bookingRepository;
  @Autowired ShowSeatRepository showSeats;
  @Autowired TestFixtures fixtures;

  @Test
  void expiredHold_isReleasedBySweeper_andSeatsCanBeReHeld() {
    TestFixtures.Seed seed = fixtures.seedShow();
    List<UUID> pickedSeatIds = seatIdsForShow(seed.show().getId(), 2);

    BookingService.BookingResult held = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);

    // With hold-ttl-minutes=0 the hold's expiresAt equals createdAt, so
    // the very first sweep tick will pick it up.
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> {
          Booking b = bookingRepository.findById(held.booking().getId()).orElseThrow();
          assertThat(b.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        });

    // Seats are back to AVAILABLE and no longer tagged with the hold id.
    List<ShowSeat> refreshedSeats = showSeats.findByShowId(seed.show().getId()).stream()
        .filter(s -> pickedSeatIds.contains(s.getId()))
        .toList();
    assertThat(refreshedSeats)
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE))
        .allSatisfy(s -> assertThat(s.getHoldBookingId()).isNull())
        .allSatisfy(s -> assertThat(s.getHeldUntil()).isNull());

    // The same seats can now be held by a fresh booking.
    BookingService.BookingResult reheld = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);
    assertThat(reheld.booking().getId()).isNotEqualTo(held.booking().getId());
    assertThat(reheld.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
  }

  private List<UUID> seatIdsForShow(UUID showId, int count) {
    return showSeats.findByShowId(showId).stream()
        .limit(count)
        .map(ShowSeat::getId)
        .toList();
  }
}
