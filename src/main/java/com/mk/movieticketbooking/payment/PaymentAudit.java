package com.mk.movieticketbooking.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes payment audit rows in an isolated transaction so a declined
 * charge (whose outer confirmation transaction rolls back) still leaves
 * a durable FAILED record for reconcile / fraud-review.
 * <p>
 * Lives in its own bean so callers can go through the Spring proxy and
 * pick up the {@code REQUIRES_NEW} propagation.
 */
@Component
public class PaymentAudit {

  private final PaymentRepository payments;

  public PaymentAudit(PaymentRepository payments) {
    this.payments = payments;
  }

  /**
   * Persists the payment row and commits its own transaction before
   * returning. Used for FAILED rows that must survive an outer rollback.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payment recordIndependently(Payment payment) {
    return payments.save(payment);
  }
}
