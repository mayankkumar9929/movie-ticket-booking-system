package com.mk.movieticketbooking.show;

import com.mk.movieticketbooking.catalog.SeatCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-category price multiplier, applied on top of the show's PricingTier
 * multiplier when materializing ShowSeat rows.
 * <p>
 * Bound to {@code app.pricing.category-surcharge} in application.yaml.
 * Any missing category defaults to 1.0 so the app degrades safely if
 * config is incomplete.
 */
@ConfigurationProperties(prefix = "app.pricing")
public class CategorySurchargeConfig {

  private Map<SeatCategory, BigDecimal> categorySurcharge = new EnumMap<>(SeatCategory.class);

  public Map<SeatCategory, BigDecimal> getCategorySurcharge() {
    return categorySurcharge;
  }

  public void setCategorySurcharge(Map<SeatCategory, BigDecimal> categorySurcharge) {
    this.categorySurcharge = categorySurcharge;
  }

  public BigDecimal multiplierFor(SeatCategory category) {
    return categorySurcharge.getOrDefault(category, BigDecimal.ONE);
  }
}
