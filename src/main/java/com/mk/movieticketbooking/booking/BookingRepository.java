package com.mk.movieticketbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

  List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, Instant cutoff);

  /**
   * CONFIRMED bookings whose show starts strictly after {@code now} and
   * at or before {@code until}, and which haven't been reminded yet.
   * The reminder scheduler pages through these one at a time.
   */
  @Query("""
      select b.id from Booking b
      where b.status = com.mk.movieticketbooking.booking.BookingStatus.CONFIRMED
        and b.remindedAt is null
        and b.show.startsAt > :now
        and b.show.startsAt <= :until
      """)
  List<UUID> findRemindableIds(@Param("now") Instant now, @Param("until") Instant until);
}
