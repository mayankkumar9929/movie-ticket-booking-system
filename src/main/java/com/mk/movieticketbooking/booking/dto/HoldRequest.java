package com.mk.movieticketbooking.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HoldRequest(
    @NotNull UUID showId,
    @NotEmpty List<@NotNull UUID> showSeatIds) {}
