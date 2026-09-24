package com.mk.movieticketbooking.catalog.dto;

import com.mk.movieticketbooking.catalog.Seat;
import com.mk.movieticketbooking.catalog.SeatCategory;

import java.util.UUID;

public record SeatResponse(
    UUID id, String rowLabel, int seatNumber, SeatCategory category) {

  public static SeatResponse from(Seat s) {
    return new SeatResponse(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getCategory());
  }
}
