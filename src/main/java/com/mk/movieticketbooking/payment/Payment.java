package com.mk.movieticketbooking.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
 * Record of a payment attempt against a booking. One booking may
 * accumulate multiple rows (a FAILED attempt followed by a SUCCESS,
 * a SUCCESS followed by a REFUNDED once the refund flow is in place).
 * <p>
 * {@code bookingId} is stored as a plain UUID rather than a JPA
 * association to keep this table self-contained — its history is
 * append-only and doesn't need to be traversed via the booking.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "booking_id", nullable = false)
  private UUID bookingId;

  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status;

  /** Mock gateway reference; a real integration would use the PSP's txn id. */
  @Column(name = "gateway_ref", length = 64)
  private String gatewayRef;

  /** Failure reason surfaced to the caller when status = FAILED. */
  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
