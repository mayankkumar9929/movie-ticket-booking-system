package com.mk.movieticketbooking.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PricingTierRequest(
    @NotBlank @Size(max = 40) String name,
    @NotNull @DecimalMin(value = "0.001", inclusive = true)
        @DecimalMax(value = "999.999", inclusive = true)
        BigDecimal multiplier,
    boolean active) {}
