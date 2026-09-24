package com.mk.movieticketbooking.pricing.dto;

import com.mk.movieticketbooking.pricing.PricingTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingTierResponse(
    UUID id, String name, BigDecimal multiplier, boolean active, Instant createdAt) {

  public static PricingTierResponse from(PricingTier t) {
    return new PricingTierResponse(
        t.getId(), t.getName(), t.getMultiplier(), t.isActive(), t.getCreatedAt());
  }
}
