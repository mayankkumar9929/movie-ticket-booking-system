package com.mk.movieticketbooking.catalog.dto;

import com.mk.movieticketbooking.catalog.City;

import java.time.Instant;
import java.util.UUID;

public record CityResponse(UUID id, String name, String state, Instant createdAt) {

  public static CityResponse from(City c) {
    return new CityResponse(c.getId(), c.getName(), c.getState(), c.getCreatedAt());
  }
}
