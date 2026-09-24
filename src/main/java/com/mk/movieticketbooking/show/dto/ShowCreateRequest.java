package com.mk.movieticketbooking.show.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShowCreateRequest(
    @NotNull UUID movieId,
    @NotNull UUID screenId,
    @NotNull UUID pricingTierId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal basePrice) {}
