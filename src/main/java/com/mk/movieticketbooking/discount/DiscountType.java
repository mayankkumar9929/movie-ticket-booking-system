package com.mk.movieticketbooking.discount;

/**
 * How a discount's {@code value} is interpreted.
 * <ul>
 *   <li>{@link #PERCENT} — {@code value} is a percentage (e.g. 10 = 10% off),
 *       optionally capped by {@code maxDiscountAmount}.</li>
 *   <li>{@link #FLAT} — {@code value} is a fixed money amount subtracted
 *       from the booking total.</li>
 * </ul>
 */
public enum DiscountType {
  PERCENT,
  FLAT
}
