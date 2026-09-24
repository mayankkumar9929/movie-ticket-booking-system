package com.mk.movieticketbooking.movie;

import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.movie.dto.MovieRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MovieService {

  private final MovieRepository movies;

  public MovieService(MovieRepository movies) {
    this.movies = movies;
  }

  public Movie create(MovieRequest req) {
    Movie m = Movie.builder()
        .id(UUID.randomUUID())
        .title(req.title())
        .durationMinutes(req.durationMinutes())
        .language(req.language())
        .rating(req.rating())
        .synopsis(req.synopsis())
        .createdAt(Instant.now())
        .build();
    return movies.save(m);
  }

  @Transactional(readOnly = true)
  public List<Movie> list(String titleFragment) {
    if (titleFragment == null || titleFragment.isBlank()) {
      return movies.findAll();
    }
    return movies.findByTitleContainingIgnoreCaseOrderByTitleAsc(titleFragment.trim());
  }

  @Transactional(readOnly = true)
  public Movie get(UUID id) {
    return movies.findById(id).orElseThrow(() -> NotFoundException.of("Movie", id));
  }

  public Movie update(UUID id, MovieRequest req) {
    Movie m = get(id);
    m.setTitle(req.title());
    m.setDurationMinutes(req.durationMinutes());
    m.setLanguage(req.language());
    m.setRating(req.rating());
    m.setSynopsis(req.synopsis());
    return m;
  }

  /**
   * Unconditional delete. Once the Show entity exists, deleting a movie
   * that has scheduled shows will be refused with 409.
   */
  public void delete(UUID id) {
    Movie m = get(id);
    movies.delete(m);
  }
}
