package com.mk.movieticketbooking.booking.dto;

import com.mk.movieticketbooking.booking.Booking;
import com.mk.movieticketbooking.booking.BookingStatus;
import com.mk.movieticketbooking.show.ShowSeat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    UUID userId,
    UUID showId,
    BookingStatus status,
    BigDecimal totalAmount,
    List<HeldSeat> seats,
    Instant createdAt,
    Instant expiresAt,
    Instant confirmedAt,
    Instant cancelledAt) {

  public record HeldSeat(
      UUID showSeatId, String rowLabel, int seatNumber, BigDecimal price) {

    public static HeldSeat from(ShowSeat ss) {
      return new HeldSeat(
          ss.getId(),
          ss.getSeat().getRowLabel(),
          ss.getSeat().getSeatNumber(),
          ss.getPriceAtShow());
    }
  }

  public static BookingResponse from(Booking b, List<ShowSeat> seats) {
    return new BookingResponse(
        b.getId(),
        b.getUserId(),
        b.getShow().getId(),
        b.getStatus(),
        b.getTotalAmount(),
        seats.stream().map(HeldSeat::from).toList(),
        b.getCreatedAt(),
        b.getExpiresAt(),
        b.getConfirmedAt(),
        b.getCancelledAt());
  }
}
