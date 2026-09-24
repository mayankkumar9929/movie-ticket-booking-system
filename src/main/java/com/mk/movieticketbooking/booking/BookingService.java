package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
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
  private final BookingProperties props;

  public BookingService(
      BookingRepository bookings,
      ShowRepository shows,
      ShowSeatRepository showSeats,
      BookingProperties props) {
    this.bookings = bookings;
    this.shows = shows;
    this.showSeats = showSeats;
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

  @Transactional(readOnly = true)
  public BookingResult get(UUID bookingId) {
    Booking b = bookings.findById(bookingId)
        .orElseThrow(() -> NotFoundException.of("Booking", bookingId));
    List<ShowSeat> seats = showSeats.findByHoldBookingId(bookingId);
    return new BookingResult(b, seats);
  }

  @Transactional(readOnly = true)
  public List<Booking> listForUser(UUID userId) {
    return bookings.findByUserIdOrderByCreatedAtDesc(userId);
  }

  /** Pair of a booking and the seats it currently holds (or held while HELD). */
  public record BookingResult(Booking booking, List<ShowSeat> seats) {}
}
