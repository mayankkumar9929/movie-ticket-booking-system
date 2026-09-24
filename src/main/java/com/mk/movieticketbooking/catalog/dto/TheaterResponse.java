package com.mk.movieticketbooking.catalog.dto;

import com.mk.movieticketbooking.catalog.Theater;

import java.time.Instant;
import java.util.UUID;

public record TheaterResponse(
    UUID id, String name, String address, UUID cityId, String cityName, Instant createdAt) {

  public static TheaterResponse from(Theater t) {
    return new TheaterResponse(
        t.getId(),
        t.getName(),
        t.getAddress(),
        t.getCity().getId(),
        t.getCity().getName(),
        t.getCreatedAt());
  }
}
