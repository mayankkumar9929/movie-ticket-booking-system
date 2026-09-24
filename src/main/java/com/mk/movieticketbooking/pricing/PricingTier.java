package com.mk.movieticketbooking.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Named price-multiplier applied to a show's base price.
 * <p>
 * Examples: REGULAR (1.00), WEEKEND (1.25), PREMIERE (1.75). The concrete
 * price a customer pays for a seat is
 * {@code baseShowPrice * pricingTier.multiplier * seatCategory.surcharge}
 * (category surcharges are configured separately and applied at booking time).
 * <p>
 * Tiers are never hard-deleted while any Show still references them; the
 * {@code active} flag is used for retirement so historical bookings remain
 * intact.
 */
@Entity
@Table(name = "pricing_tiers")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PricingTier {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** Stored uppercased, unique. */
  @Column(name = "name", nullable = false, unique = true, length = 40)
  private String name;

  @Column(name = "multiplier", nullable = false, precision = 6, scale = 3)
  private BigDecimal multiplier;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
