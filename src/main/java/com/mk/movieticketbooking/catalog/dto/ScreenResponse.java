package com.mk.movieticketbooking.catalog.dto;

import com.mk.movieticketbooking.catalog.Screen;

import java.time.Instant;
import java.util.UUID;

public record ScreenResponse(
    UUID id, String name, UUID theaterId, String theaterName, Instant createdAt) {

  public static ScreenResponse from(Screen s) {
    return new ScreenResponse(
        s.getId(),
        s.getName(),
        s.getTheater().getId(),
        s.getTheater().getName(),
        s.getCreatedAt());
  }
}
