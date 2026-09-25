package com.mk.movieticketbooking.support;

import com.mk.movieticketbooking.catalog.City;
import com.mk.movieticketbooking.catalog.CityRepository;
import com.mk.movieticketbooking.catalog.Screen;
import com.mk.movieticketbooking.catalog.ScreenRepository;
import com.mk.movieticketbooking.catalog.Seat;
import com.mk.movieticketbooking.catalog.SeatCategory;
import com.mk.movieticketbooking.catalog.SeatRepository;
import com.mk.movieticketbooking.catalog.Theater;
import com.mk.movieticketbooking.catalog.TheaterRepository;
import com.mk.movieticketbooking.movie.Movie;
import com.mk.movieticketbooking.movie.MovieRating;
import com.mk.movieticketbooking.movie.MovieRepository;
import com.mk.movieticketbooking.pricing.PricingTier;
import com.mk.movieticketbooking.pricing.PricingTierRepository;
import com.mk.movieticketbooking.show.Show;
import com.mk.movieticketbooking.show.ShowService;
import com.mk.movieticketbooking.show.dto.ShowCreateRequest;
import com.mk.movieticketbooking.user.Role;
import com.mk.movieticketbooking.user.User;
import com.mk.movieticketbooking.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a minimal domain fixture for integration tests: one city, one
 * theater, one screen with a small seat grid, one movie, one active
 * pricing tier, one scheduled show, and one CUSTOMER user.
 * <p>
 * Every seeder returns the persisted entity so the caller can pluck out
 * ids and reference them directly.
 */
@Component
@Transactional
public class TestFixtures {

  private final CityRepository cities;
  private final TheaterRepository theaters;
  private final ScreenRepository screens;
  private final SeatRepository seats;
  private final MovieRepository movies;
  private final PricingTierRepository tiers;
  private final UserRepository users;
  private final ShowService showService;

  public TestFixtures(
      CityRepository cities,
      TheaterRepository theaters,
      ScreenRepository screens,
      SeatRepository seats,
      MovieRepository movies,
      PricingTierRepository tiers,
      UserRepository users,
      ShowService showService) {
    this.cities = cities;
    this.theaters = theaters;
    this.screens = screens;
    this.seats = seats;
    this.movies = movies;
    this.tiers = tiers;
    this.users = users;
    this.showService = showService;
  }

  /** Full happy-path seed. Show starts in ~48h, ends 2h later, base 100. */
  public Seed seedShow() {
    return seedShow(Instant.now().plus(Duration.ofHours(48)));
  }

  /** Full seed with a caller-chosen show start time. */
  public Seed seedShow(Instant startsAt) {
    City city = cities.save(City.builder()
        .id(UUID.randomUUID())
        .name("Testville-" + shortId())
        .state("TS")
        .createdAt(Instant.now())
        .build());

    Theater theater = theaters.save(Theater.builder()
        .id(UUID.randomUUID())
        .name("Test Theater " + shortId())
        .address("1 Test St")
        .city(city)
        .createdAt(Instant.now())
        .build());

    Screen screen = screens.save(Screen.builder()
        .id(UUID.randomUUID())
        .name("Screen 1")
        .theater(theater)
        .createdAt(Instant.now())
        .build());

    // 2x3 STANDARD grid — enough for concurrent booking scenarios.
    List<Seat> screenSeats = new ArrayList<>();
    for (String row : List.of("A", "B")) {
      for (int n = 1; n <= 3; n++) {
        screenSeats.add(seats.save(Seat.builder()
            .id(UUID.randomUUID())
            .screen(screen)
            .rowLabel(row)
            .seatNumber(n)
            .category(SeatCategory.STANDARD)
            .build()));
      }
    }

    Movie movie = movies.save(Movie.builder()
        .id(UUID.randomUUID())
        .title("Test Movie " + shortId())
        .durationMinutes(120)
        .language("English")
        .rating(MovieRating.U)
        .synopsis("A test film.")
        .createdAt(Instant.now())
        .build());

    PricingTier tier = tiers.save(PricingTier.builder()
        .id(UUID.randomUUID())
        .name("REGULAR-" + shortId())
        .multiplier(new BigDecimal("1.000"))
        .active(true)
        .createdAt(Instant.now())
        .build());

    Show show = showService.create(new ShowCreateRequest(
        movie.getId(),
        screen.getId(),
        tier.getId(),
        startsAt,
        startsAt.plus(Duration.ofHours(2)),
        new BigDecimal("100.00")));

    User user = users.save(User.builder()
        .id(UUID.randomUUID())
        .email("test-" + shortId() + "@example.com")
        .passwordHash("$2a$10$notarealhash")
        .name("Test Customer")
        .role(Role.CUSTOMER)
        .createdAt(Instant.now())
        .build());

    return new Seed(city, theater, screen, screenSeats, movie, tier, show, user);
  }

  /** Persist an additional CUSTOMER user. Handy for cross-user tests. */
  public User createCustomer() {
    return users.save(User.builder()
        .id(UUID.randomUUID())
        .email("test-" + shortId() + "@example.com")
        .passwordHash("$2a$10$notarealhash")
        .name("Extra Customer")
        .role(Role.CUSTOMER)
        .createdAt(Instant.now())
        .build());
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /** The seeded aggregate returned as one bundle. */
  public record Seed(
      City city,
      Theater theater,
      Screen screen,
      List<Seat> seats,
      Movie movie,
      PricingTier tier,
      Show show,
      User user) {}
}
