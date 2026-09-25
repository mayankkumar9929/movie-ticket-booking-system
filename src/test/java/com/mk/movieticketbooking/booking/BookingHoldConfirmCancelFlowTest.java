package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.payment.Payment;
import com.mk.movieticketbooking.payment.PaymentMethod;
import com.mk.movieticketbooking.payment.PaymentRepository;
import com.mk.movieticketbooking.payment.PaymentStatus;
import com.mk.movieticketbooking.refund.RefundPolicyService;
import com.mk.movieticketbooking.refund.dto.RefundPolicyTierRequest;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowSeatStatus;
import com.mk.movieticketbooking.support.IntegrationTest;
import com.mk.movieticketbooking.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end happy path across the booking lifecycle: hold → confirm →
 * cancel. Verifies seat state transitions, booking status transitions,
 * payment records, and refund amount at each step.
 */
@IntegrationTest
class BookingHoldConfirmCancelFlowTest {

  @Autowired BookingService bookings;
  @Autowired ShowSeatRepository showSeats;
  @Autowired PaymentRepository payments;
  @Autowired RefundPolicyService refundPolicy;
  @Autowired TestFixtures fixtures;

  @Test
  void hold_confirm_cancel_refundsFullyWhenSeededTier() {
    TestFixtures.Seed seed = fixtures.seedShow();
    // 100% refund for cancellations at least 24h out. Our seeded show
    // starts 48h from now, so this tier will apply.
    refundPolicy.create(new RefundPolicyTierRequest(24, new BigDecimal("100.00")));

    List<UUID> pickedSeatIds = seatIdsForShow(seed.show().getId(), 2);

    // --- HOLD ---
    BookingService.BookingResult held = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);

    assertThat(held.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
    // 2 STANDARD seats @ base 100 * multiplier 1.0 * surcharge 1.0 = 200
    assertThat(held.booking().getTotalAmount())
        .isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(held.seats())
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.HELD))
        .allSatisfy(s -> assertThat(s.getHoldBookingId())
            .isEqualTo(held.booking().getId()));

    // --- CONFIRM ---
    BookingService.ConfirmResult confirmed = bookings.confirm(
        seed.user().getId(),
        held.booking().getId(),
        PaymentMethod.CARD,
        "4111111111111111",
        null);

    assertThat(confirmed.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(confirmed.booking().getConfirmedAt()).isNotNull();
    assertThat(confirmed.booking().getExpiresAt()).isNull();
    assertThat(confirmed.payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(confirmed.payment().getAmount())
        .isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(confirmed.seats())
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.BOOKED))
        .allSatisfy(s -> assertThat(s.getBookingId()).isEqualTo(held.booking().getId()))
        .allSatisfy(s -> assertThat(s.getHoldBookingId()).isNull());

    // --- CANCEL ---
    BookingService.CancelResult cancelled = bookings.cancel(
        seed.user().getId(), held.booking().getId());

    assertThat(cancelled.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(cancelled.booking().getCancelledAt()).isNotNull();
    assertThat(cancelled.refund()).isNotNull();
    assertThat(cancelled.refund().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(cancelled.refund().getAmount())
        .isEqualByComparingTo(new BigDecimal("200.00"));

    // Seats released back to AVAILABLE — the cancelled booking is no
    // longer linked to any seat row.
    List<ShowSeat> releasedSeats = showSeats.findByBookingId(held.booking().getId());
    assertThat(releasedSeats).isEmpty();
    List<ShowSeat> allSeats = showSeats.findByShowId(seed.show().getId());
    assertThat(allSeats)
        .filteredOn(s -> pickedSeatIds.contains(s.getId()))
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));

    // Two payment rows total: the original SUCCESS and the REFUNDED refund.
    List<Payment> paymentHistory =
        payments.findByBookingIdOrderByCreatedAtDesc(held.booking().getId());
    assertThat(paymentHistory).hasSize(2);
    assertThat(paymentHistory)
        .extracting(Payment::getStatus)
        .containsExactlyInAnyOrder(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
  }

  private List<UUID> seatIdsForShow(UUID showId, int count) {
    return showSeats.findByShowId(showId).stream()
        .limit(count)
        .map(ShowSeat::getId)
        .toList();
  }
}
