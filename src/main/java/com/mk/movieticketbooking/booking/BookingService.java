package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.discount.DiscountService;
import com.mk.movieticketbooking.payment.Payment;
import com.mk.movieticketbooking.payment.PaymentGateway;
import com.mk.movieticketbooking.payment.PaymentMethod;
import com.mk.movieticketbooking.payment.PaymentRepository;
import com.mk.movieticketbooking.payment.PaymentStatus;
import com.mk.movieticketbooking.show.Show;
import com.mk.movieticketbooking.show.ShowRepository;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowSeatStatus;
import com.mk.movieticketbooking.show.ShowStatus;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Booking hold and lookup. This is the concurrency-critical service.
 * <p>
 * A hold operation:
 * <ol>
 *   <li>loads the requested {@code ShowSeat} rows,</li>
 *   <li>verifies every one is AVAILABLE and belongs to the given show,</li>
 *   <li>flips them to HELD, stamping {@code holdBookingId} and {@code heldUntil},</li>
 *   <li>persists a PENDING {@code Booking} with the summed total.</li>
 * </ol>
 * The {@code @Version} column on {@code ShowSeat} ensures that if two
 * transactions race for the same seat, exactly one commit succeeds; the
 * other surfaces as {@code OptimisticLockException} which we translate
 * into a 409. The unique {@code (show_id, seat_id)} constraint is a
 * second belt-and-braces guard.
 */
@Service
@Transactional
public class BookingService {

  private static final Logger log = LoggerFactory.getLogger(BookingService.class);

  private final BookingRepository bookings;
  private final ShowRepository shows;
  private final ShowSeatRepository showSeats;
  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final DiscountService discounts;
  private final BookingProperties props;

  public BookingService(
      BookingRepository bookings,
      ShowRepository shows,
      ShowSeatRepository showSeats,
      PaymentRepository payments,
      PaymentGateway gateway,
      DiscountService discounts,
      BookingProperties props) {
    this.bookings = bookings;
    this.shows = shows;
    this.showSeats = showSeats;
    this.payments = payments;
    this.gateway = gateway;
    this.discounts = discounts;
    this.props = props;
  }

  /**
   * Places an all-or-nothing hold on the requested seats for the given
   * user. Returns the persisted booking together with the seats it holds.
   *
   * @throws NotFoundException  if the show doesn't exist
   * @throws BadRequestException  if the show isn't SCHEDULED or any seat
   *     id doesn't belong to it
   * @throws ConflictException  if any requested seat is already HELD or
   *     BOOKED, or a concurrent transaction won the race
   */
  public BookingResult hold(UUID userId, UUID showId, List<UUID> requestedSeatIds) {
    // De-duplicate defensively — clients may repeat ids in the list.
    Set<UUID> ids = new HashSet<>(requestedSeatIds);
    if (ids.isEmpty()) {
      throw new BadRequestException("At least one seat must be selected");
    }

    Show show = shows.findById(showId)
        .orElseThrow(() -> NotFoundException.of("Show", showId));
    if (show.getStatus() != ShowStatus.SCHEDULED) {
      throw new BadRequestException("Show is not open for booking");
    }
    if (!Instant.now().isBefore(show.getStartsAt())) {
      throw new BadRequestException("Show has already started");
    }

    List<ShowSeat> seats = showSeats.findByShowIdAndIdIn(showId, ids);
    if (seats.size() != ids.size()) {
      throw new BadRequestException(
          "One or more seat ids don't belong to this show");
    }

    Instant now = Instant.now();
    for (ShowSeat ss : seats) {
      if (ss.getStatus() != ShowSeatStatus.AVAILABLE) {
        throw new ConflictException(
            "Seat " + ss.getSeat().getRowLabel() + ss.getSeat().getSeatNumber()
                + " is not available");
      }
    }

    UUID bookingId = UUID.randomUUID();
    Instant expiresAt = now.plus(props.holdTtl());

    BigDecimal total = BigDecimal.ZERO;
    for (ShowSeat ss : seats) {
      ss.setStatus(ShowSeatStatus.HELD);
      ss.setHeldUntil(expiresAt);
      ss.setHoldBookingId(bookingId);
      total = total.add(ss.getPriceAtShow());
    }

    Booking booking = Booking.builder()
        .id(bookingId)
        .userId(userId)
        .show(show)
        .totalAmount(total)
        .status(BookingStatus.PENDING)
        .createdAt(now)
        .expiresAt(expiresAt)
        .build();

    try {
      showSeats.saveAll(seats);
      bookings.save(booking);
      // Flush inside the try so version-conflict / uniqueness errors surface
      // here and get translated, not at the end of the transaction where
      // the caller can't distinguish them.
      showSeats.flush();
      bookings.flush();
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      log.debug("Hold lost the race on show={} seats={}", showId, ids);
      throw new ConflictException(
          "One or more selected seats were just taken; please retry");
    } catch (DataIntegrityViolationException ex) {
      log.debug("Hold hit a constraint violation on show={} seats={}: {}",
          showId, ids, ex.getMostSpecificCause().getMessage());
      throw new ConflictException(
          "One or more selected seats were just taken; please retry");
    }

    return new BookingResult(booking, seats);
  }

  /**
   * Confirms a PENDING booking by charging the payment gateway and, on
   * success, flipping its HELD seats to BOOKED.
   * <p>
   * The whole operation runs in one transaction so the payment record,
   * booking state, seat state, and discount redemption advance together.
   * Idempotent for already-CONFIRMED bookings; refused for EXPIRED,
   * CANCELLED, or expired-but-not-yet-swept holds.
   * <p>
   * A {@code discountCode} is optional. If supplied it is validated,
   * priced, and redeemed atomically alongside the confirmation; the
   * gateway is charged the discounted amount, and the applied code and
   * amount are snapshotted onto the booking. A failed charge does not
   * consume a discount usage.
   *
   * @throws NotFoundException if the booking doesn't exist or doesn't
   *     belong to {@code userId} (opaque to avoid an existence leak)
   * @throws ConflictException if the booking has expired, been cancelled,
   *     or its seats have been released by the sweeper
   * @throws BadRequestException if the gateway declines the payment or
   *     the discount code is unusable
   */
  public ConfirmResult confirm(UUID userId, UUID bookingId, PaymentMethod method,
      String instrument, String discountCode) {
    Booking booking = bookings.findById(bookingId)
        .orElseThrow(() -> NotFoundException.of("Booking", bookingId));

    if (!booking.getUserId().equals(userId)) {
      // Ownership check lives here so a compromised controller can't
      // sidestep it. NotFound (not Forbidden) to avoid existence leak.
      throw NotFoundException.of("Booking", bookingId);
    }

    if (booking.getStatus() == BookingStatus.CONFIRMED) {
      // Idempotent: return the existing payment.
      Payment existing = payments.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
          .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Confirmed booking has no SUCCESS payment: " + bookingId));
      return new ConfirmResult(booking, seatsFor(booking), existing);
    }
    if (booking.getStatus() != BookingStatus.PENDING) {
      throw new ConflictException(
          "Booking is " + booking.getStatus() + " and cannot be confirmed");
    }
    Instant now = Instant.now();
    if (booking.getExpiresAt() != null && !now.isBefore(booking.getExpiresAt())) {
      throw new ConflictException("Booking hold has expired");
    }

    List<ShowSeat> seats = showSeats.findByHoldBookingId(bookingId);
    if (seats.isEmpty()) {
      // Sweeper released the seats between the read above and here.
      throw new ConflictException("Booking hold has expired");
    }
    for (ShowSeat ss : seats) {
      if (ss.getStatus() != ShowSeatStatus.HELD
          || !bookingId.equals(ss.getHoldBookingId())) {
        throw new ConflictException("Booking hold has expired");
      }
    }

    // Resolve and apply a discount code if one was supplied. The quote
    // step validates and computes; the redeem step atomically bumps the
    // usage count under optimistic locking.
    DiscountService.Application applied = null;
    BigDecimal amountPayable = booking.getTotalAmount();
    if (discountCode != null && !discountCode.isBlank()) {
      applied = discounts.quote(discountCode, booking.getTotalAmount());
      amountPayable = booking.getTotalAmount().subtract(applied.amount());
    }

    PaymentGateway.ChargeResult charge =
        gateway.charge(bookingId, amountPayable, method, instrument);

    Payment payment = Payment.builder()
        .id(UUID.randomUUID())
        .bookingId(bookingId)
        .amount(amountPayable)
        .method(method)
        .status(charge.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
        .gatewayRef(charge.gatewayRef())
        .failureReason(charge.failureReason())
        .createdAt(now)
        .build();
    payments.save(payment);

    if (!charge.success()) {
      // Persist the FAILED payment record and abort. Seats stay HELD;
      // the customer can retry until the hold expires. No discount
      // usage is consumed for a failed charge.
      throw new BadRequestException("Payment declined: " + charge.failureReason());
    }

    if (applied != null) {
      // Redeem inside the confirm transaction so a concurrent race for
      // the last slot rolls back the whole confirmation on failure.
      discounts.redeem(applied.discount().getId());
      booking.setDiscountCode(applied.discount().getCode());
      booking.setDiscountAmount(applied.amount());
    }

    for (ShowSeat ss : seats) {
      ss.setStatus(ShowSeatStatus.BOOKED);
      ss.setHeldUntil(null);
      ss.setHoldBookingId(null);
      ss.setBookingId(bookingId);
    }
    booking.setStatus(BookingStatus.CONFIRMED);
    booking.setConfirmedAt(now);
    booking.setExpiresAt(null);

    try {
      showSeats.saveAll(seats);
      bookings.save(booking);
      showSeats.flush();
      bookings.flush();
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      // Concurrent state change on the seat rows (e.g. sweeper firing at
      // the same instant). The payment succeeded but we can't complete
      // the booking — surface as 409 and rely on the payment record for
      // an out-of-band reconcile in a real system.
      log.warn("Confirm lost the race on booking={}: {}", bookingId, ex.getMessage());
      throw new ConflictException(
          "Booking state changed during confirmation; please retry");
    }

    return new ConfirmResult(booking, seats, payment);
  }

  @Transactional(readOnly = true)
  public BookingResult get(UUID bookingId) {
    Booking b = bookings.findById(bookingId)
        .orElseThrow(() -> NotFoundException.of("Booking", bookingId));
    List<ShowSeat> seats = seatsFor(b);
    return new BookingResult(b, seats);
  }

  @Transactional(readOnly = true)
  public List<Booking> listForUser(UUID userId) {
    return bookings.findByUserIdOrderByCreatedAtDesc(userId);
  }

  /**
   * IDs of PENDING bookings whose hold has elapsed. Called by the sweeper
   * as the outer "find work" step; each id is then expired in its own
   * transaction so a single conflict doesn't roll back the whole batch.
   */
  @Transactional(readOnly = true)
  public List<UUID> findExpiredHoldIds(Instant cutoff) {
    return bookings.findByStatusAndExpiresAtBefore(BookingStatus.PENDING, cutoff)
        .stream()
        .map(Booking::getId)
        .toList();
  }

  /**
   * Expires a single PENDING booking: flips it to EXPIRED and releases
   * its HELD seats back to AVAILABLE. Runs in its own transaction
   * ({@code REQUIRES_NEW}) so one bad row can't fail the whole sweep.
   * <p>
   * No-op if the booking has advanced to CONFIRMED/CANCELLED/EXPIRED in
   * the meantime, or if an optimistic-lock conflict fires (a concurrent
   * confirm just claimed the seats — the sweep will re-check next tick).
   *
   * @return {@code true} if this call performed the expiry, {@code false}
   *     if the booking was already advanced or a race was lost
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean expireOne(UUID bookingId) {
    Booking b = bookings.findById(bookingId).orElse(null);
    if (b == null || b.getStatus() != BookingStatus.PENDING) {
      return false;
    }
    Instant now = Instant.now();
    if (b.getExpiresAt() == null || now.isBefore(b.getExpiresAt())) {
      // Not actually expired yet — the "cutoff" the caller used was
      // stale relative to expiresAt. Leave it alone.
      return false;
    }

    List<ShowSeat> seats = showSeats.findByHoldBookingId(bookingId);
    for (ShowSeat ss : seats) {
      if (ss.getStatus() == ShowSeatStatus.HELD
          && bookingId.equals(ss.getHoldBookingId())) {
        ss.setStatus(ShowSeatStatus.AVAILABLE);
        ss.setHeldUntil(null);
        ss.setHoldBookingId(null);
      }
      // Any other state means a concurrent confirm won — leave the row
      // untouched and let the version check below decide.
    }
    b.setStatus(BookingStatus.EXPIRED);
    b.setExpiresAt(null);

    try {
      showSeats.saveAll(seats);
      bookings.save(b);
      showSeats.flush();
      bookings.flush();
      return true;
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      log.debug("Sweeper lost race on booking={}: {}", bookingId, ex.getMessage());
      return false;
    }
  }

  /**
   * Seats belonging to a booking. While PENDING they are attached via
   * {@code holdBookingId}; once CONFIRMED (or REFUNDED after cancel) they
   * are attached via {@code bookingId}.
   */
  List<ShowSeat> seatsFor(Booking b) {
    return switch (b.getStatus()) {
      case PENDING -> showSeats.findByHoldBookingId(b.getId());
      case CONFIRMED, CANCELLED -> showSeats.findByBookingId(b.getId());
      case EXPIRED -> List.of();
    };
  }

  /** Pair of a booking and the seats it currently holds (or held while HELD). */
  public record BookingResult(Booking booking, List<ShowSeat> seats) {}

  /** Result of a successful (or idempotent) confirmation. */
  public record ConfirmResult(Booking booking, List<ShowSeat> seats, Payment payment) {}
}
