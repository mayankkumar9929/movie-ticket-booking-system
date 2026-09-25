package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.discount.Discount;
import com.mk.movieticketbooking.discount.DiscountRepository;
import com.mk.movieticketbooking.discount.DiscountService;
import com.mk.movieticketbooking.discount.DiscountType;
import com.mk.movieticketbooking.discount.dto.DiscountRequest;
import com.mk.movieticketbooking.payment.PaymentMethod;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.support.IntegrationTest;
import com.mk.movieticketbooking.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Discount lifecycle at confirmation time: valid code discounts the
 * charged amount and consumes a usage; an exhausted code is refused
 * before the charge and does not consume a usage on a failure.
 */
@IntegrationTest
class BookingConfirmWithDiscountTest {

  @Autowired BookingService bookings;
  @Autowired DiscountService discounts;
  @Autowired DiscountRepository discountRepository;
  @Autowired ShowSeatRepository showSeats;
  @Autowired TestFixtures fixtures;

  @Test
  void percentDiscount_isAppliedAndUsageIncremented() {
    TestFixtures.Seed seed = fixtures.seedShow();
    Discount code = seedDiscount("SAVE10", DiscountType.PERCENT, new BigDecimal("10.00"), 5);

    List<UUID> pickedSeatIds = seatIdsForShow(seed.show().getId(), 2);
    BookingService.BookingResult held = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);
    // 2 STANDARD seats @ 100 = 200 total, 10% off = 180 payable.

    BookingService.ConfirmResult confirmed = bookings.confirm(
        seed.user().getId(),
        held.booking().getId(),
        PaymentMethod.CARD,
        "4111111111111111",
        "save10");

    assertThat(confirmed.payment().getAmount())
        .isEqualByComparingTo(new BigDecimal("180.00"));
    assertThat(confirmed.booking().getDiscountCode()).isEqualTo("SAVE10");
    assertThat(confirmed.booking().getDiscountAmount())
        .isEqualByComparingTo(new BigDecimal("20.00"));

    Discount refreshed = discountRepository.findById(code.getId()).orElseThrow();
    assertThat(refreshed.getUsedCount()).isEqualTo(1);
  }

  @Test
  void exhaustedDiscount_isRefused_andNoUsageIsConsumed() {
    TestFixtures.Seed seed = fixtures.seedShow();
    Discount code = seedDiscount("ONESHOT", DiscountType.FLAT, new BigDecimal("50.00"), 1);
    // Pre-consume the single available usage.
    discounts.redeem(code.getId());
    assertThat(discountRepository.findById(code.getId()).orElseThrow().getUsedCount())
        .isEqualTo(1);

    List<UUID> pickedSeatIds = seatIdsForShow(seed.show().getId(), 1);
    BookingService.BookingResult held = bookings.hold(
        seed.user().getId(), seed.show().getId(), pickedSeatIds);

    assertThatThrownBy(() -> bookings.confirm(
        seed.user().getId(),
        held.booking().getId(),
        PaymentMethod.CARD,
        "4111111111111111",
        "ONESHOT"))
        .isInstanceOf(BadRequestException.class);

    // The failed confirm did NOT re-bump usedCount (still 1 from the
    // manual pre-consume).
    assertThat(discountRepository.findById(code.getId()).orElseThrow().getUsedCount())
        .isEqualTo(1);
    // Booking is still PENDING — the customer can retry without a code.
    // (BookingService loads a fresh Booking, so we re-fetch too.)
    assertThat(bookings.get(held.booking().getId()).booking().getStatus())
        .isEqualTo(BookingStatus.PENDING);
  }

  private Discount seedDiscount(String code, DiscountType type, BigDecimal value, int limit) {
    Instant now = Instant.now();
    return discounts.create(new DiscountRequest(
        code,
        type,
        value,
        null,
        null,
        now.minus(Duration.ofHours(1)),
        now.plus(Duration.ofDays(30)),
        limit,
        true));
  }

  private List<UUID> seatIdsForShow(UUID showId, int count) {
    return showSeats.findByShowId(showId).stream()
        .limit(count)
        .map(ShowSeat::getId)
        .toList();
  }
}
