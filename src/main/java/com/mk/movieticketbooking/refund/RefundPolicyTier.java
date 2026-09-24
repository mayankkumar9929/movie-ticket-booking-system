package com.mk.movieticketbooking.refund;

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
 * A single tier of a refund policy. The applicable tier for a
 * cancellation is the one with the largest {@code minHoursBeforeShow}
 * still {@code <=} the hours between the cancellation and the show
 * start. Tiers with no match => 0% refund (cancellation still allowed
 * only before the show starts).
 */
@Entity
@Table(name = "refund_policy_tiers")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefundPolicyTier {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * Minimum hours-before-show for this tier to apply. E.g. a tier with
   * {@code minHoursBeforeShow = 24} and {@code refundPercent = 100} means
   * "cancellations at least 24 hours before the show refund 100%".
   */
  @Column(name = "min_hours_before_show", nullable = false)
  private int minHoursBeforeShow;

  /** 0–100 percent of paid amount refunded when this tier applies. */
  @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal refundPercent;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
