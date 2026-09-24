package com.mk.movieticketbooking.show;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {

  boolean existsByMovieId(UUID movieId);

  boolean existsByPricingTierId(UUID pricingTierId);

  boolean existsByScreenId(UUID screenId);

  /**
   * Returns SCHEDULED shows on the given screen whose interval overlaps
   * [{@code startsAt}, {@code endsAt}). Used to reject double-booking a screen.
   * <p>
   * Two intervals overlap iff one starts before the other ends AND vice versa.
   */
  @Query("""
      SELECT s FROM Show s
       WHERE s.screen.id = :screenId
         AND s.status = com.mk.movieticketbooking.show.ShowStatus.SCHEDULED
         AND s.startsAt < :endsAt
         AND s.endsAt > :startsAt
      """)
  List<Show> findOverlapping(
      @Param("screenId") UUID screenId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt);

  /**
   * Public catalog query: SCHEDULED shows filtered by movie, city (via
   * screen → theater → city), and a start-time window. Any nullable filter
   * is a wildcard.
   */
  @Query("""
      SELECT s FROM Show s
       WHERE s.status = com.mk.movieticketbooking.show.ShowStatus.SCHEDULED
         AND (:movieId IS NULL OR s.movie.id = :movieId)
         AND (:cityId  IS NULL OR s.screen.theater.city.id = :cityId)
         AND (:from    IS NULL OR s.startsAt >= :from)
         AND (:to      IS NULL OR s.startsAt <  :to)
       ORDER BY s.startsAt ASC
      """)
  List<Show> findScheduled(
      @Param("movieId") UUID movieId,
      @Param("cityId") UUID cityId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /**
   * Distinct movies that currently have a SCHEDULED show in the given city
   * (or across all cities if {@code cityId} is null).
   */
  @Query("""
      SELECT DISTINCT s.movie.id FROM Show s
       WHERE s.status = com.mk.movieticketbooking.show.ShowStatus.SCHEDULED
         AND (:cityId IS NULL OR s.screen.theater.city.id = :cityId)
      """)
  List<UUID> findMovieIdsWithScheduledShows(@Param("cityId") UUID cityId);
}
