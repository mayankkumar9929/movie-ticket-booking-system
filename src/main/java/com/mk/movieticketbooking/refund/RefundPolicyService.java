package com.mk.movieticketbooking.refund;

import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.refund.dto.RefundPolicyTierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for refund policy tiers, plus the compute step used by
 * the booking cancellation flow.
 * <p>
 * A policy is the ordered list of active tiers. The applicable tier
 * for a given cancellation is the one with the largest
 * {@code minHoursBeforeShow} still {@code <=} the hours-until-show at
 * the cancellation instant. If no tier matches, refund is zero.
 */
@Service
@Transactional
public class RefundPolicyService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final RefundPolicyTierRepository tiers;

  public RefundPolicyService(RefundPolicyTierRepository tiers) {
    this.tiers = tiers;
  }

  public RefundPolicyTier create(RefundPolicyTierRequest req) {
    // Uniqueness on the threshold keeps the tier list unambiguous.
    boolean exists = tiers.findAll().stream()
        .anyMatch(t -> t.getMinHoursBeforeShow() == req.minHoursBeforeShow());
    if (exists) {
      throw new ConflictException(
          "A tier already exists at " + req.minHoursBeforeShow() + " hours");
    }
    RefundPolicyTier t = RefundPolicyTier.builder()
        .id(UUID.randomUUID())
        .minHoursBeforeShow(req.minHoursBeforeShow())
        .refundPercent(req.refundPercent().setScale(2, RoundingMode.HALF_UP))
        .createdAt(Instant.now())
        .build();
    return tiers.save(t);
  }

  public RefundPolicyTier update(UUID id, RefundPolicyTierRequest req) {
    RefundPolicyTier t = get(id);
    if (t.getMinHoursBeforeShow() != req.minHoursBeforeShow()) {
      boolean clash = tiers.findAll().stream()
          .anyMatch(other -> !other.getId().equals(id)
              && other.getMinHoursBeforeShow() == req.minHoursBeforeShow());
      if (clash) {
        throw new ConflictException(
            "A tier already exists at " + req.minHoursBeforeShow() + " hours");
      }
      t.setMinHoursBeforeShow(req.minHoursBeforeShow());
    }
    t.setRefundPercent(req.refundPercent().setScale(2, RoundingMode.HALF_UP));
    return t;
  }

  @Transactional(readOnly = true)
  public RefundPolicyTier get(UUID id) {
    return tiers.findById(id)
        .orElseThrow(() -> NotFoundException.of("RefundPolicyTier", id));
  }

  @Transactional(readOnly = true)
  public List<RefundPolicyTier> list() {
    return tiers.findAllByOrderByMinHoursBeforeShowDesc();
  }

  public void delete(UUID id) {
    tiers.delete(get(id));
  }

  /**
   * Computes the refundable amount for the given paid amount and time
   * until show. Returns zero if no tier matches or if the show has
   * already started.
   */
  @Transactional(readOnly = true)
  public BigDecimal computeRefund(BigDecimal paidAmount, Instant now, Instant showStartsAt) {
    if (!now.isBefore(showStartsAt)) {
      return BigDecimal.ZERO.setScale(2);
    }
    long hoursUntilShow = Duration.between(now, showStartsAt).toHours();
    BigDecimal percent = tiers.findAllByOrderByMinHoursBeforeShowDesc().stream()
        .filter(t -> hoursUntilShow >= t.getMinHoursBeforeShow())
        .map(RefundPolicyTier::getRefundPercent)
        .findFirst()
        .orElse(BigDecimal.ZERO);
    return paidAmount
        .multiply(percent)
        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
  }
}
