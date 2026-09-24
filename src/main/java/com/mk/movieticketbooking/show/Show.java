package com.mk.movieticketbooking.show;

import com.mk.movieticketbooking.catalog.Screen;
import com.mk.movieticketbooking.movie.Movie;
import com.mk.movieticketbooking.pricing.PricingTier;
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
 * A scheduled screening of a Movie on a Screen at a specific time, priced
 * by a PricingTier.
 * <p>
 * Business rules enforced at service level:
 * <ul>
 *   <li>{@code endsAt} strictly after {@code startsAt}.</li>
 *   <li>No overlapping SCHEDULED shows on the same Screen.</li>
 *   <li>{@code pricingTier} must be active at creation.</li>
 * </ul>
 */
@Entity
@Table(name = "shows")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Show {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "screen_id", nullable = false)
  private Screen screen;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pricing_tier_id", nullable = false)
  private PricingTier pricingTier;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ShowStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
