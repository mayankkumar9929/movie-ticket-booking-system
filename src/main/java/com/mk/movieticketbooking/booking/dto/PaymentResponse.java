package com.mk.movieticketbooking.booking.dto;

import com.mk.movieticketbooking.payment.Payment;
import com.mk.movieticketbooking.payment.PaymentMethod;
import com.mk.movieticketbooking.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID bookingId,
    BigDecimal amount,
    PaymentMethod method,
    PaymentStatus status,
    String gatewayRef,
    String failureReason,
    Instant createdAt) {

  public static PaymentResponse from(Payment p) {
    return new PaymentResponse(
        p.getId(),
        p.getBookingId(),
        p.getAmount(),
        p.getMethod(),
        p.getStatus(),
        p.getGatewayRef(),
        p.getFailureReason(),
        p.getCreatedAt());
  }
}
