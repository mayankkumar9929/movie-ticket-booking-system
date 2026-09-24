package com.mk.movieticketbooking.show.dto;

import com.mk.movieticketbooking.show.Show;
import com.mk.movieticketbooking.show.ShowStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShowResponse(
    UUID id,
    UUID movieId,
    String movieTitle,
    UUID screenId,
    String screenName,
    UUID theaterId,
    String theaterName,
    UUID pricingTierId,
    String pricingTierName,
    Instant startsAt,
    Instant endsAt,
    BigDecimal basePrice,
    ShowStatus status,
    Instant createdAt) {

  public static ShowResponse from(Show s) {
    return new ShowResponse(
        s.getId(),
        s.getMovie().getId(),
        s.getMovie().getTitle(),
        s.getScreen().getId(),
        s.getScreen().getName(),
        s.getScreen().getTheater().getId(),
        s.getScreen().getTheater().getName(),
        s.getPricingTier().getId(),
        s.getPricingTier().getName(),
        s.getStartsAt(),
        s.getEndsAt(),
        s.getBasePrice(),
        s.getStatus(),
        s.getCreatedAt());
  }
}
