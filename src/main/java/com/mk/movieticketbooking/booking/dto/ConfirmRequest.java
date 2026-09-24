package com.mk.movieticketbooking.booking.dto;

import com.mk.movieticketbooking.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmRequest(
    @NotNull PaymentMethod method,
    @NotBlank String instrument,
    @Size(max = 40) String discountCode) {}
