package com.mk.movieticketbooking.refund.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RefundPolicyTierRequest(
    @NotNull @PositiveOrZero Integer minHoursBeforeShow,
    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @DecimalMax(value = "100.00", inclusive = true)
    BigDecimal refundPercent) {}
