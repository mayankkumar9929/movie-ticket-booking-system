package com.mk.movieticketbooking.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A film in the catalog. Independent of any theater/show — one movie can be
 * scheduled as many shows across many screens.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Movie {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "language", nullable = false, length = 40)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(name = "rating", nullable = false, length = 4)
  private MovieRating rating;

  @Column(name = "synopsis", length = 2000)
  private String synopsis;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
