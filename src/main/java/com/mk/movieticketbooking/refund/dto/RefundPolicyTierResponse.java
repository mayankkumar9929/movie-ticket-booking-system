package com.mk.movieticketbooking.refund.dto;

import com.mk.movieticketbooking.refund.RefundPolicyTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundPolicyTierResponse(
    UUID id,
    int minHoursBeforeShow,
    BigDecimal refundPercent,
    Instant createdAt) {

  public static RefundPolicyTierResponse from(RefundPolicyTier t) {
    return new RefundPolicyTierResponse(
        t.getId(),
        t.getMinHoursBeforeShow(),
        t.getRefundPercent(),
        t.getCreatedAt());
  }
}
