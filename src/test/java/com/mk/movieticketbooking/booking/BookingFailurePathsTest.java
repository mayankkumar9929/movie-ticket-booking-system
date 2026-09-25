package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.payment.Payment;
import com.mk.movieticketbooking.payment.PaymentMethod;
import com.mk.movieticketbooking.payment.PaymentRepository;
import com.mk.movieticketbooking.payment.PaymentStatus;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowSeatStatus;
import com.mk.movieticketbooking.support.IntegrationTest;
import com.mk.movieticketbooking.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure paths that keep booking state consistent: a declined payment
 * leaves seats HELD (retryable), and two overlapping hold requests
 * cannot both succeed even when they name overlapping seat sets.
 */
@IntegrationTest
class BookingFailurePathsTest {

  @Autowired BookingService bookings;
  @Autowired PaymentRepository payments;
  @Autowired ShowSeatRepository showSeats;
  @Autowired TestFixtures fixtures;

  @Test
  void declinedPayment_leavesBookingPending_andRecordsFailedPayment() {
    TestFixtures.Seed seed = fixtures.seedShow();
    List<UUID> pickedSeatIds = seatIdsForShow(seed.show().getId(), 2);

    BookingService.BookingResult held = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);

    // MockPaymentGateway declines any card ending in 0000.
    assertThatThrownBy(() -> bookings.confirm(
        seed.user().getId(),
        held.booking().getId(),
        PaymentMethod.CARD,
        "4111111111110000",
        null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Payment declined");

    // Booking stays PENDING so the customer can retry with another card.
    Booking refreshed = bookings.get(held.booking().getId()).booking();
    assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.PENDING);

    // Seats are still HELD by the same booking.
    List<ShowSeat> heldSeats = showSeats.findByHoldBookingId(held.booking().getId());
    assertThat(heldSeats)
        .hasSize(pickedSeatIds.size())
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.HELD));

    // A FAILED payment row is persisted for auditability.
    List<Payment> paymentHistory =
        payments.findByBookingIdOrderByCreatedAtDesc(held.booking().getId());
    assertThat(paymentHistory)
        .hasSize(1)
        .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
  }

  @Test
  void overlappingHoldRequest_isRejectedWithConflict() {
    TestFixtures.Seed seed = fixtures.seedShow();
    var otherUser = fixtures.createCustomer();
    List<UUID> allSeatIds = seatIdsForShow(seed.show().getId(), 3);
    List<UUID> firstPick = allSeatIds.subList(0, 2);     // seats 0, 1
    List<UUID> overlap = allSeatIds.subList(1, 3);       // seats 1, 2  (shares seat 1)

    bookings.hold(seed.user().getId(), seed.show().getId(), firstPick);

    // The second hold shares seat 1 with the first — the whole request
    // must fail (all-or-nothing) and leave seat 2 untouched.
    assertThatThrownBy(
        () -> bookings.hold(otherUser.getId(), seed.show().getId(), overlap))
        .isInstanceOf(ConflictException.class);

    List<ShowSeat> allShowSeats = showSeats.findByShowId(seed.show().getId()).stream()
        .filter(s -> allSeatIds.contains(s.getId()))
        .toList();
    // seat 0, 1 → HELD by the first booking; seat 2 → still AVAILABLE
    // (the second request never partially took it).
    assertThat(allShowSeats)
        .filteredOn(s -> firstPick.contains(s.getId()))
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.HELD));
    assertThat(allShowSeats)
        .filteredOn(s -> s.getId().equals(allSeatIds.get(2)))
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
  }

  private List<UUID> seatIdsForShow(UUID showId, int count) {
    return showSeats.findByShowId(showId).stream()
        .limit(count)
        .map(ShowSeat::getId)
        .toList();
  }
}
