package com.mk.movieticketbooking.browse;

import com.mk.movieticketbooking.catalog.City;
import com.mk.movieticketbooking.catalog.CityRepository;
import com.mk.movieticketbooking.catalog.Theater;
import com.mk.movieticketbooking.catalog.TheaterRepository;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.movie.Movie;
import com.mk.movieticketbooking.movie.MovieRepository;
import com.mk.movieticketbooking.show.Show;
import com.mk.movieticketbooking.show.ShowRepository;
import com.mk.movieticketbooking.show.ShowSeat;
import com.mk.movieticketbooking.show.ShowSeatRepository;
import com.mk.movieticketbooking.show.ShowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public read-only browsing of the catalog and scheduled shows.
 * Every method is transactional-readonly so lazy associations can be
 * traversed inside DTO mappers without detached-entity surprises.
 */
@Service
@Transactional(readOnly = true)
public class BrowseService {

  private final CityRepository cities;
  private final TheaterRepository theaters;
  private final MovieRepository movies;
  private final ShowRepository shows;
  private final ShowSeatRepository showSeats;

  public BrowseService(
      CityRepository cities,
      TheaterRepository theaters,
      MovieRepository movies,
      ShowRepository shows,
      ShowSeatRepository showSeats) {
    this.cities = cities;
    this.theaters = theaters;
    this.movies = movies;
    this.shows = shows;
    this.showSeats = showSeats;
  }

  public List<City> listCities() {
    return cities.findAll();
  }

  public List<Theater> theatersInCity(UUID cityId) {
    if (!cities.existsById(cityId)) {
      throw NotFoundException.of("City", cityId);
    }
    return theaters.findByCityId(cityId);
  }

  public Theater getTheater(UUID theaterId) {
    return theaters.findById(theaterId)
        .orElseThrow(() -> NotFoundException.of("Theater", theaterId));
  }

  /**
   * Movies with at least one SCHEDULED show. If {@code cityId} is given,
   * the show must be in that city.
   */
  public List<Movie> listPlayingMovies(UUID cityId) {
    if (cityId != null && !cities.existsById(cityId)) {
      throw NotFoundException.of("City", cityId);
    }
    List<UUID> ids = shows.findMovieIdsWithScheduledShows(cityId);
    return ids.isEmpty() ? List.of() : movies.findAllById(ids);
  }

  public Movie getMovie(UUID movieId) {
    return movies.findById(movieId)
        .orElseThrow(() -> NotFoundException.of("Movie", movieId));
  }

  public List<Show> listScheduledShows(UUID movieId, UUID cityId, Instant from, Instant to) {
    return shows.findScheduled(movieId, cityId, from, to);
  }

  public Show getScheduledShow(UUID showId) {
    Show s = shows.findById(showId)
        .orElseThrow(() -> NotFoundException.of("Show", showId));
    if (s.getStatus() != ShowStatus.SCHEDULED) {
      // Cancelled shows aren't part of the public catalog.
      throw NotFoundException.of("Show", showId);
    }
    return s;
  }

  public List<ShowSeat> seatMap(UUID showId) {
    getScheduledShow(showId);
    return showSeats.findByShowId(showId);
  }
}
