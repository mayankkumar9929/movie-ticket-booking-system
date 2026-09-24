package com.mk.movieticketbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

  List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, Instant cutoff);
}
