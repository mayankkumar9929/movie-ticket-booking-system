package com.mk.movieticketbooking.show.dto;

import com.mk.movieticketbooking.catalog.SeatCategory;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ShowSeatResponse(
    UUID id,
    UUID seatId,
    String rowLabel,
    int seatNumber,
    SeatCategory category,
    ShowSeatStatus status,
    BigDecimal priceAtShow) {

  public static ShowSeatResponse from(ShowSeat ss) {
    var seat = ss.getSeat();
    return new ShowSeatResponse(
        ss.getId(),
        seat.getId(),
        seat.getRowLabel(),
        seat.getSeatNumber(),
        seat.getCategory(),
        ss.getStatus(),
        ss.getPriceAtShow());
  }
}
