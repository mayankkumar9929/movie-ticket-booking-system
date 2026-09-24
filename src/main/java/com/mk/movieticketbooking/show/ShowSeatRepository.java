package com.mk.movieticketbooking.show;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

  List<ShowSeat> findByShowId(UUID showId);

  List<ShowSeat> findByShowIdAndIdIn(UUID showId, Collection<UUID> ids);

  List<ShowSeat> findByHoldBookingId(UUID holdBookingId);

  boolean existsByShowId(UUID showId);

  void deleteByShowId(UUID showId);
}
