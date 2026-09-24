package com.mk.movieticketbooking.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * A discount code. Codes are stored uppercase and unique.
 * <p>
 * {@code usageLimit} is the total number of confirmations this code may
 * be applied to; {@code null} means unlimited. {@code usedCount} is
 * incremented atomically during confirmation under {@code @Version}
 * optimistic locking so two concurrent last-slot redemptions cannot
 * both succeed.
 */
@Entity
@Table(name = "discounts")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Discount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 10)
  private DiscountType type;

  /** For PERCENT: 0–100 (e.g. 10 = 10%). For FLAT: money amount. */
  @Column(name = "value", nullable = false, precision = 12, scale = 2)
  private BigDecimal value;

  /** Cap on the amount discounted when {@code type == PERCENT}. Null = no cap. */
  @Column(name = "max_discount_amount", precision = 12, scale = 2)
  private BigDecimal maxDiscountAmount;

  /** Booking total must reach this to qualify. Null = no minimum. */
  @Column(name = "min_booking_amount", precision = 12, scale = 2)
  private BigDecimal minBookingAmount;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  /** Total permitted redemptions across all users. Null = unlimited. */
  @Column(name = "usage_limit")
  private Integer usageLimit;

  @Column(name = "used_count", nullable = false)
  private int usedCount;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
