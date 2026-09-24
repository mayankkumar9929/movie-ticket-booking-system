package com.mk.movieticketbooking.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment gateway abstraction. A real integration (Stripe, Razorpay, …)
 * would implement this against its SDK; for this project a deterministic
 * mock is wired in via {@link MockPaymentGateway}.
 */
public interface PaymentGateway {

  /**
   * Charges the given amount. Implementations MUST be side-effect-free on
   * failure so the caller can retry.
   *
   * @param bookingId  the booking being paid for (used for idempotency /
   *                   traceability by real gateways)
   * @param amount     the amount to charge
   * @param method     the tender
   * @param instrument opaque instrument identifier (e.g. card number)
   */
  ChargeResult charge(
      UUID bookingId, BigDecimal amount, PaymentMethod method, String instrument);

  /**
   * Refunds a previously-successful charge (identified by its
   * {@code gatewayRef}). Real gateways may partial-refund; here we
   * simply pass the intended amount.
   */
  RefundResult refund(String originalGatewayRef, BigDecimal amount);

  /** Outcome of a charge attempt. */
  record ChargeResult(
      boolean success, String gatewayRef, String failureReason) {

    public static ChargeResult ok(String ref) {
      return new ChargeResult(true, ref, null);
    }

    public static ChargeResult failed(String reason) {
      return new ChargeResult(false, null, reason);
    }
  }

  /** Outcome of a refund attempt. */
  record RefundResult(boolean success, String gatewayRef, String failureReason) {

    public static RefundResult ok(String ref) {
      return new RefundResult(true, ref, null);
    }

    public static RefundResult failed(String reason) {
      return new RefundResult(false, null, reason);
    }
  }
}
