package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.show.Show;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
 * A booking aggregate: a customer holding one or more {@code ShowSeat}s
 * for a specific show, transitioning through {@link BookingStatus}.
 * <p>
 * The seats themselves live on {@code ShowSeat} and reference this booking
 * via {@code holdBookingId} while HELD. The link is intentionally by id
 * (not a JPA association) because the seat table is the concurrency
 * anchor and we want to keep its writes light.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Booking {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** The customer who owns this booking. */
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  /**
   * Discount code applied at confirmation (uppercase, snapshot). Null if
   * none was used. Frozen here so admin edits to the discount don't
   * rewrite booking history.
   */
  @Column(name = "discount_code", length = 40)
  private String discountCode;

  /** Amount subtracted by the discount code at confirmation. Null if none. */
  @Column(name = "discount_amount", precision = 12, scale = 2)
  private BigDecimal discountAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BookingStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** When the current PENDING hold expires. Null once CONFIRMED / EXPIRED / CANCELLED. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  /**
   * When the pre-show reminder was sent for this booking, or null if it
   * hasn't been. Used by the reminder scheduler as an idempotency marker
   * so a booking is reminded exactly once even across restarts.
   */
  @Column(name = "reminded_at")
  private Instant remindedAt;
}
