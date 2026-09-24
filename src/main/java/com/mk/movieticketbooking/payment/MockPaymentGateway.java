package com.mk.movieticketbooking.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic mock payment gateway for local development.
 * <p>
 * Behaviour:
 * <ul>
 *   <li>a card number ending in {@code 0000} fails with "insufficient funds"</li>
 *   <li>a null or blank instrument fails with "missing card details"</li>
 *   <li>anything else succeeds and returns a synthetic gateway reference</li>
 * </ul>
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

  @Override
  public ChargeResult charge(
      UUID bookingId, BigDecimal amount, PaymentMethod method, String instrument) {
    if (instrument == null || instrument.isBlank()) {
      return ChargeResult.failed("missing card details");
    }
    String trimmed = instrument.trim();
    if (trimmed.endsWith("0000")) {
      return ChargeResult.failed("insufficient funds");
    }
    return ChargeResult.ok("mock_" + UUID.randomUUID());
  }
}
