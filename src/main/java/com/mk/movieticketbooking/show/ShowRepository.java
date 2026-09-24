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
}
