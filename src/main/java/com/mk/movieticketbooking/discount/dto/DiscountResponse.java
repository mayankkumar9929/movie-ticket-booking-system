package com.mk.movieticketbooking.discount.dto;

import com.mk.movieticketbooking.discount.Discount;
import com.mk.movieticketbooking.discount.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DiscountResponse(
    UUID id,
    String code,
    DiscountType type,
    BigDecimal value,
    BigDecimal maxDiscountAmount,
    BigDecimal minBookingAmount,
    Instant validFrom,
    Instant validUntil,
    Integer usageLimit,
    int usedCount,
    boolean active,
    Instant createdAt) {

  public static DiscountResponse from(Discount d) {
    return new DiscountResponse(
        d.getId(),
        d.getCode(),
        d.getType(),
        d.getValue(),
        d.getMaxDiscountAmount(),
        d.getMinBookingAmount(),
        d.getValidFrom(),
        d.getValidUntil(),
        d.getUsageLimit(),
        d.getUsedCount(),
        d.isActive(),
        d.getCreatedAt());
  }
}
