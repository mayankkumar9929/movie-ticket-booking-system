package com.mk.movieticketbooking.show;

import com.mk.movieticketbooking.catalog.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-show state of a single seat. This is the <b>concurrency anchor</b>
 * for the booking flow: hold and book operations mutate this row under
 * optimistic locking so exactly one caller wins per contested seat.
 * <p>
 * State transitions:
 * <pre>
 *   AVAILABLE ── hold ──> HELD ── confirm ──> BOOKED
 *                          │
 *                          └── expire / cancel ──> AVAILABLE
 * </pre>
 * A unique constraint on {@code (show_id, seat_id)} is a belt-and-braces
 * guard on top of the version check.
 */
@Entity
@Table(
    name = "show_seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_show_seat_show_seat",
        columnNames = {"show_id", "seat_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShowSeat {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_id", nullable = false)
  private Seat seat;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ShowSeatStatus status;

  /** When the current HELD state expires. Null unless status = HELD. */
  @Column(name = "held_until")
  private Instant heldUntil;

  /** The Booking currently holding this seat. Null unless status = HELD. */
  @Column(name = "hold_booking_id")
  private UUID holdBookingId;

  /**
   * The Booking that owns this seat once BOOKED. Set at confirmation and
   * kept through refunds so the customer's booking history has stable
   * seat details even after {@code holdBookingId} is cleared.
   */
  @Column(name = "booking_id")
  private UUID bookingId;

  /**
   * Price frozen at show-creation time so admin edits to PricingTier /
   * category surcharges do not affect issued or in-flight bookings.
   */
  @Column(name = "price_at_show", nullable = false, precision = 12, scale = 2)
  private BigDecimal priceAtShow;

  /** Optimistic-lock column — every write increments it. */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
