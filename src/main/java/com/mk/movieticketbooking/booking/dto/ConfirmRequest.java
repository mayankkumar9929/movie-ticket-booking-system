package com.mk.movieticketbooking.booking.dto;

import com.mk.movieticketbooking.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmRequest(
    @NotNull PaymentMethod method,
    @NotBlank String instrument) {}
