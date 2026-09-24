package com.mk.movieticketbooking.movie.dto;

import com.mk.movieticketbooking.movie.Movie;
import com.mk.movieticketbooking.movie.MovieRating;

import java.time.Instant;
import java.util.UUID;

public record MovieResponse(
    UUID id,
    String title,
    int durationMinutes,
    String language,
    MovieRating rating,
    String synopsis,
    Instant createdAt) {

  public static MovieResponse from(Movie m) {
    return new MovieResponse(
        m.getId(),
        m.getTitle(),
        m.getDurationMinutes(),
        m.getLanguage(),
        m.getRating(),
        m.getSynopsis(),
        m.getCreatedAt());
  }
}
