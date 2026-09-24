package com.mk.movieticketbooking.discount.dto;

import com.mk.movieticketbooking.discount.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountRequest(
    @NotBlank @Size(max = 40) String code,
    @NotNull DiscountType type,
    @NotNull @DecimalMin(value = "0.01") BigDecimal value,
    @DecimalMin(value = "0.01") BigDecimal maxDiscountAmount,
    @DecimalMin(value = "0.00") BigDecimal minBookingAmount,
    @NotNull Instant validFrom,
    @NotNull Instant validUntil,
    @Positive Integer usageLimit,
    Boolean active) {}
