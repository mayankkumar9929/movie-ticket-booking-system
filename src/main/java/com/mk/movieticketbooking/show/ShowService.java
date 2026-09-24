package com.mk.movieticketbooking.show;

import com.mk.movieticketbooking.catalog.Screen;
import com.mk.movieticketbooking.catalog.ScreenRepository;
import com.mk.movieticketbooking.catalog.Seat;
import com.mk.movieticketbooking.catalog.SeatRepository;
import com.mk.movieticketbooking.common.exception.BadRequestException;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.movie.Movie;
import com.mk.movieticketbooking.movie.MovieRepository;
import com.mk.movieticketbooking.pricing.PricingTier;
import com.mk.movieticketbooking.pricing.PricingTierRepository;
import com.mk.movieticketbooking.show.dto.ShowCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin operations on Shows and their ShowSeat materializations.
 * <p>
 * A Show's price for each seat is computed once at creation and frozen onto
 * the ShowSeat row so subsequent edits to PricingTier or category surcharges
 * do not retroactively change ticket prices.
 */
@Service
@Transactional
public class ShowService {

  private final ShowRepository shows;
  private final ShowSeatRepository showSeats;
  private final MovieRepository movies;
  private final ScreenRepository screens;
  private final SeatRepository seats;
  private final PricingTierRepository tiers;
  private final CategorySurchargeConfig surcharges;

  public ShowService(
      ShowRepository shows,
      ShowSeatRepository showSeats,
      MovieRepository movies,
      ScreenRepository screens,
      SeatRepository seats,
      PricingTierRepository tiers,
      CategorySurchargeConfig surcharges) {
    this.shows = shows;
    this.showSeats = showSeats;
    this.movies = movies;
    this.screens = screens;
    this.seats = seats;
    this.tiers = tiers;
    this.surcharges = surcharges;
  }

  /**
   * Creates a Show and materializes one ShowSeat per seat in the screen.
   * All-or-nothing: any failure rolls back the show and all its seats.
   */
  public Show create(ShowCreateRequest req) {
    if (!req.endsAt().isAfter(req.startsAt())) {
      throw new BadRequestException("endsAt must be after startsAt");
    }

    Movie movie = movies.findById(req.movieId())
        .orElseThrow(() -> NotFoundException.of("Movie", req.movieId()));
    Screen screen = screens.findById(req.screenId())
        .orElseThrow(() -> NotFoundException.of("Screen", req.screenId()));
    PricingTier tier = tiers.findById(req.pricingTierId())
        .orElseThrow(() -> NotFoundException.of("PricingTier", req.pricingTierId()));
    if (!tier.isActive()) {
      throw new BadRequestException("Pricing tier is inactive: " + tier.getName());
    }

    List<Seat> screenSeats =
        seats.findByScreenIdOrderByRowLabelAscSeatNumberAsc(req.screenId());
    if (screenSeats.isEmpty()) {
      throw new BadRequestException("Screen has no seat layout defined");
    }

    List<Show> overlaps =
        shows.findOverlapping(req.screenId(), req.startsAt(), req.endsAt());
    if (!overlaps.isEmpty()) {
      throw new ConflictException(
          "Screen already has a show overlapping this time window");
    }

    Show show = Show.builder()
        .id(UUID.randomUUID())
        .movie(movie)
        .screen(screen)
        .pricingTier(tier)
        .startsAt(req.startsAt())
        .endsAt(req.endsAt())
        .basePrice(req.basePrice())
        .status(ShowStatus.SCHEDULED)
        .createdAt(Instant.now())
        .build();
    shows.save(show);

    List<ShowSeat> materialized = new ArrayList<>(screenSeats.size());
    for (Seat seat : screenSeats) {
      BigDecimal price = req.basePrice()
          .multiply(tier.getMultiplier())
          .multiply(surcharges.multiplierFor(seat.getCategory()))
          .setScale(2, RoundingMode.HALF_UP);
      materialized.add(ShowSeat.builder()
          .id(UUID.randomUUID())
          .show(show)
          .seat(seat)
          .status(ShowSeatStatus.AVAILABLE)
          .priceAtShow(price)
          .build());
    }
    showSeats.saveAll(materialized);
    return show;
  }

  @Transactional(readOnly = true)
  public List<Show> list(UUID movieId, UUID screenId, Instant from, Instant to) {
    // Simple filter chain — for a take-home the dataset is tiny; we can
    // reach for Specifications / QueryDSL later if this grows.
    return shows.findAll().stream()
        .filter(s -> movieId == null || s.getMovie().getId().equals(movieId))
        .filter(s -> screenId == null || s.getScreen().getId().equals(screenId))
        .filter(s -> from == null || !s.getStartsAt().isBefore(from))
        .filter(s -> to == null || !s.getStartsAt().isAfter(to))
        .toList();
  }

  @Transactional(readOnly = true)
  public Show get(UUID id) {
    return shows.findById(id).orElseThrow(() -> NotFoundException.of("Show", id));
  }

  @Transactional(readOnly = true)
  public List<ShowSeat> listSeats(UUID showId) {
    get(showId); // 404 if the show doesn't exist
    return showSeats.findByShowId(showId);
  }

  /**
   * Cancels the show. Refund handling for existing bookings is added with
   * the refund policy commit; for now the operation is refused if any
   * seat has been booked.
   */
  public Show cancel(UUID id) {
    Show s = get(id);
    if (s.getStatus() == ShowStatus.CANCELLED) {
      return s;
    }
    boolean hasBookings = showSeats.findByShowId(id).stream()
        .anyMatch(ss -> ss.getStatus() == ShowSeatStatus.BOOKED
            || ss.getStatus() == ShowSeatStatus.HELD);
    if (hasBookings) {
      throw new ConflictException(
          "Show has active holds or confirmed bookings; cancel requires refund handling");
    }
    s.setStatus(ShowStatus.CANCELLED);
    return s;
  }

  /**
   * Hard-deletes a show and its ShowSeat rows. Refused if any seat has
   * been held or booked.
   */
  public void delete(UUID id) {
    Show s = get(id);
    boolean hasActivity = showSeats.findByShowId(id).stream()
        .anyMatch(ss -> ss.getStatus() != ShowSeatStatus.AVAILABLE);
    if (hasActivity) {
      throw new ConflictException(
          "Show has holds or bookings; cancel it instead of deleting");
    }
    showSeats.deleteByShowId(id);
    shows.delete(s);
  }
}
