package com.mk.movieticketbooking.browse;

import com.mk.movieticketbooking.catalog.dto.CityResponse;
import com.mk.movieticketbooking.catalog.dto.TheaterResponse;
import com.mk.movieticketbooking.movie.dto.MovieResponse;
import com.mk.movieticketbooking.show.dto.ShowResponse;
import com.mk.movieticketbooking.show.dto.ShowSeatResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public browsing endpoints. Unauthenticated; safe to expose because they
 * expose only SCHEDULED shows and their current seat availability.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicBrowseController {

  private final BrowseService browse;

  public PublicBrowseController(BrowseService browse) {
    this.browse = browse;
  }

  @GetMapping("/cities")
  public List<CityResponse> cities() {
    return browse.listCities().stream().map(CityResponse::from).toList();
  }

  @GetMapping("/cities/{cityId}/theaters")
  public List<TheaterResponse> theatersInCity(@PathVariable UUID cityId) {
    return browse.theatersInCity(cityId).stream().map(TheaterResponse::from).toList();
  }

  @GetMapping("/theaters/{theaterId}")
  public TheaterResponse theater(@PathVariable UUID theaterId) {
    return TheaterResponse.from(browse.getTheater(theaterId));
  }

  @GetMapping("/movies")
  public List<MovieResponse> movies(@RequestParam(required = false) UUID cityId) {
    return browse.listPlayingMovies(cityId).stream().map(MovieResponse::from).toList();
  }

  @GetMapping("/movies/{movieId}")
  public MovieResponse movie(@PathVariable UUID movieId) {
    return MovieResponse.from(browse.getMovie(movieId));
  }

  @GetMapping("/shows")
  public List<ShowResponse> shows(
      @RequestParam(required = false) UUID movieId,
      @RequestParam(required = false) UUID cityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return browse.listScheduledShows(movieId, cityId, from, to).stream()
        .map(ShowResponse::from)
        .toList();
  }

  @GetMapping("/shows/{showId}")
  public ShowResponse show(@PathVariable UUID showId) {
    return ShowResponse.from(browse.getScheduledShow(showId));
  }

  @GetMapping("/shows/{showId}/seats")
  public List<ShowSeatResponse> seats(@PathVariable UUID showId) {
    return browse.seatMap(showId).stream().map(ShowSeatResponse::from).toList();
  }
}
