package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowSeatStatus;
import com.mk.movieticketbooking.support.IntegrationTest;
import com.mk.movieticketbooking.support.TestFixtures;
import com.mk.movieticketbooking.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent booking correctness under contention.
 * <p>
 * The DB is the truth and {@code ShowSeat.@Version} + the unique
 * {@code (show_id, seat_id)} constraint are the guards. This test fires
 * many parallel {@code hold()} attempts at the same seat set from
 * distinct users and asserts that <em>exactly one</em> succeeds while
 * the rest fail with {@link ConflictException}. If the guard ever
 * regressed to allow two winners, both threads would advance the seat
 * to HELD under different {@code holdBookingId}s and this test would
 * fail loudly.
 * <p>
 * The test method is intentionally not {@code @Transactional} — threads
 * must each commit their own attempt so contention actually reaches
 * the database.
 * <p>
 * Note that H2 serializes writes internally, so losers may surface as
 * {@code DataIntegrityViolationException} (unique {@code (show_id,
 * seat_id)} constraint) rather than {@code OptimisticLockException}.
 * Both paths are translated to {@link ConflictException} by
 * {@code BookingService.hold}, which is what this test asserts.
 */
@IntegrationTest
class ConcurrentHoldContentionTest {

  private static final int CONTENDING_THREADS = 12;

  @Autowired BookingService bookings;
  @Autowired ShowSeatRepository showSeats;
  @Autowired TestFixtures fixtures;

  @Test
  void manyUsersRacingForSameSeats_exactlyOneSucceeds() throws Exception {
    TestFixtures.Seed seed = fixtures.seedShow();
    UUID showId = seed.show().getId();
    // Two seats — enough to prove the all-or-nothing property while
    // keeping every thread contending for the same set.
    List<UUID> contestedSeatIds = showSeats.findByShowId(showId).stream()
        .limit(2)
        .map(ShowSeat::getId)
        .toList();

    // Pre-create N users so no one is blocked on user creation during
    // the contention window.
    List<UUID> userIds = new ArrayList<>(CONTENDING_THREADS);
    for (int i = 0; i < CONTENDING_THREADS; i++) {
      User u = fixtures.createCustomer();
      userIds.add(u.getId());
    }

    ExecutorService pool = Executors.newFixedThreadPool(CONTENDING_THREADS);
    // Barrier makes every thread wait until they're all lined up, so
    // the actual hold() calls fire within microseconds of each other.
    CyclicBarrier startGate = new CyclicBarrier(CONTENDING_THREADS);
    CountDownLatch done = new CountDownLatch(CONTENDING_THREADS);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    AtomicInteger unexpected = new AtomicInteger();
    List<UUID> winningBookingIds = new ArrayList<>();

    try {
      for (int i = 0; i < CONTENDING_THREADS; i++) {
        UUID userId = userIds.get(i);
        pool.submit(() -> {
          try {
            startGate.await(5, TimeUnit.SECONDS);
            BookingService.BookingResult r =
                bookings.hold(userId, showId, contestedSeatIds);
            successes.incrementAndGet();
            synchronized (winningBookingIds) {
              winningBookingIds.add(r.booking().getId());
            }
          } catch (ConflictException expected) {
            conflicts.incrementAndGet();
          } catch (Throwable t) {
            unexpected.incrementAndGet();
          } finally {
            done.countDown();
          }
        });
      }
      assertThat(done.await(30, TimeUnit.SECONDS))
          .as("all contending threads completed")
          .isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(unexpected.get())
        .as("no unexpected exceptions from hold()")
        .isZero();
    assertThat(successes.get())
        .as("exactly one hold succeeded")
        .isEqualTo(1);
    assertThat(conflicts.get())
        .as("all other holds were rejected with ConflictException")
        .isEqualTo(CONTENDING_THREADS - 1);

    // DB-side assertion: both contested seats are HELD under the single
    // winning booking id, and no seat is in an inconsistent state.
    List<ShowSeat> refreshedContested = showSeats.findByShowId(showId).stream()
        .filter(s -> contestedSeatIds.contains(s.getId()))
        .toList();
    assertThat(refreshedContested).hasSize(contestedSeatIds.size());
    UUID winner = winningBookingIds.get(0);
    assertThat(refreshedContested)
        .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(ShowSeatStatus.HELD))
        .allSatisfy(s -> assertThat(s.getHoldBookingId()).isEqualTo(winner));
  }
}
