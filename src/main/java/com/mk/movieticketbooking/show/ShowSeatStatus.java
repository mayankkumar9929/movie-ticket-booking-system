package com.mk.movieticketbooking.show;

/**
 * State of one seat within one show.
 * <ul>
 *   <li>{@code AVAILABLE} — free to hold.</li>
 *   <li>{@code HELD} — reserved for a pending Booking until {@code heldUntil};
 *       reclaimed if the customer does not confirm in time.</li>
 *   <li>{@code BOOKED} — payment succeeded, seat is issued.</li>
 * </ul>
 */
public enum ShowSeatStatus {
  AVAILABLE,
  HELD,
  BOOKED
}
