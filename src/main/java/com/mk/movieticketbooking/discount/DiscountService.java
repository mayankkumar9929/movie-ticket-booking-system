package com.mk.movieticketbooking.discount;

import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.discount.dto.DiscountRequest;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for discount codes plus the validate-and-redeem hook used
 * during booking confirmation.
 */
@Service
@Transactional
public class DiscountService {

  private static final Logger log = LoggerFactory.getLogger(DiscountService.class);
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final DiscountRepository discounts;

  public DiscountService(DiscountRepository discounts) {
    this.discounts = discounts;
  }

  public Discount create(DiscountRequest req) {
    String code = normalize(req.code());
    if (discounts.existsByCode(code)) {
      throw new ConflictException("Discount code already exists: " + code);
    }
    validateRequest(req);
    Discount d = Discount.builder()
        .id(UUID.randomUUID())
        .code(code)
        .type(req.type())
        .value(req.value())
        .maxDiscountAmount(req.maxDiscountAmount())
        .minBookingAmount(req.minBookingAmount())
        .validFrom(req.validFrom())
        .validUntil(req.validUntil())
        .usageLimit(req.usageLimit())
        .usedCount(0)
        .active(req.active() == null || req.active())
        .createdAt(Instant.now())
        .build();
    return discounts.save(d);
  }

  public Discount update(UUID id, DiscountRequest req) {
    Discount d = get(id);
    String code = normalize(req.code());
    if (!d.getCode().equals(code) && discounts.existsByCode(code)) {
      throw new ConflictException("Discount code already exists: " + code);
    }
    validateRequest(req);
    d.setCode(code);
    d.setType(req.type());
    d.setValue(req.value());
    d.setMaxDiscountAmount(req.maxDiscountAmount());
    d.setMinBookingAmount(req.minBookingAmount());
    d.setValidFrom(req.validFrom());
    d.setValidUntil(req.validUntil());
    d.setUsageLimit(req.usageLimit());
    if (req.active() != null) {
      d.setActive(req.active());
    }
    return d;
  }

  @Transactional(readOnly = true)
  public Discount get(UUID id) {
    return discounts.findById(id)
        .orElseThrow(() -> NotFoundException.of("Discount", id));
  }

  @Transactional(readOnly = true)
  public List<Discount> list() {
    return discounts.findAll();
  }

  public void delete(UUID id) {
    Discount d = get(id);
    if (d.getUsedCount() > 0) {
      // Preserve history: bookings snapshot the code string, but deleting
      // a code that has been redeemed loses admin-side auditability.
      throw new ConflictException(
          "Discount has been redeemed; deactivate it instead of deleting");
    }
    discounts.delete(d);
  }

  /**
   * Resolves a code, validates it against the given booking total, and
   * returns the computed discount amount. Does NOT increment usage —
   * that happens in {@link #redeem}.
   *
   * @throws BadRequestException if the code is unknown, inactive, out of
   *     its validity window, below the minimum, or exhausted
   */
  @Transactional(readOnly = true)
  public Application quote(String code, BigDecimal bookingTotal) {
    Discount d = discounts.findByCode(normalize(code))
        .orElseThrow(() -> new BadRequestException("Unknown discount code"));
    checkUsable(d, bookingTotal, Instant.now());
    BigDecimal amount = compute(d, bookingTotal);
    return new Application(d, amount);
  }

  /**
   * Increments {@code usedCount} on a previously-quoted discount, guarded
   * by the optimistic-lock version. Called from within the confirmation
   * transaction. Throws if a concurrent redemption exhausted the code.
   */
  public void redeem(UUID discountId) {
    Discount d = discounts.findById(discountId)
        .orElseThrow(() -> NotFoundException.of("Discount", discountId));
    if (d.getUsageLimit() != null && d.getUsedCount() >= d.getUsageLimit()) {
      throw new BadRequestException("Discount code is exhausted");
    }
    d.setUsedCount(d.getUsedCount() + 1);
    try {
      discounts.saveAndFlush(d);
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      log.debug("Discount redeem lost race on id={}", discountId);
      throw new BadRequestException("Discount code is exhausted");
    }
  }

  private void checkUsable(Discount d, BigDecimal bookingTotal, Instant now) {
    if (!d.isActive()) {
      throw new BadRequestException("Discount code is inactive");
    }
    if (now.isBefore(d.getValidFrom()) || !now.isBefore(d.getValidUntil())) {
      throw new BadRequestException("Discount code is not currently valid");
    }
    if (d.getUsageLimit() != null && d.getUsedCount() >= d.getUsageLimit()) {
      throw new BadRequestException("Discount code is exhausted");
    }
    if (d.getMinBookingAmount() != null
        && bookingTotal.compareTo(d.getMinBookingAmount()) < 0) {
      throw new BadRequestException(
          "Booking total is below the minimum for this code");
    }
  }

  private BigDecimal compute(Discount d, BigDecimal bookingTotal) {
    BigDecimal amount = switch (d.getType()) {
      case PERCENT -> bookingTotal
          .multiply(d.getValue())
          .divide(HUNDRED, 2, RoundingMode.HALF_UP);
      case FLAT -> d.getValue();
    };
    if (d.getMaxDiscountAmount() != null
        && amount.compareTo(d.getMaxDiscountAmount()) > 0) {
      amount = d.getMaxDiscountAmount();
    }
    // Never discount beyond the total.
    if (amount.compareTo(bookingTotal) > 0) {
      amount = bookingTotal;
    }
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private void validateRequest(DiscountRequest req) {
    if (!req.validUntil().isAfter(req.validFrom())) {
      throw new BadRequestException("validUntil must be after validFrom");
    }
    if (req.type() == DiscountType.PERCENT
        && req.value().compareTo(HUNDRED) > 0) {
      throw new BadRequestException("Percent discount cannot exceed 100");
    }
  }

  private static String normalize(String code) {
    return code == null ? null : code.trim().toUpperCase();
  }

  /** A resolved discount together with the amount it will subtract. */
  public record Application(Discount discount, BigDecimal amount) {}
}
