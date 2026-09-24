package com.mk.movieticketbooking.movie;

import com.mk.movieticketbooking.movie.dto.MovieRequest;
import com.mk.movieticketbooking.movie.dto.MovieResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/movies")
public class AdminMovieController {

  private final MovieService svc;

  public AdminMovieController(MovieService svc) {
    this.svc = svc;
  }

  @PostMapping
  public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(MovieResponse.from(svc.create(req)));
  }

  @GetMapping
  public List<MovieResponse> list(@RequestParam(required = false) String title) {
    return svc.list(title).stream().map(MovieResponse::from).toList();
  }

  @GetMapping("/{id}")
  public MovieResponse get(@PathVariable UUID id) {
    return MovieResponse.from(svc.get(id));
  }

  @PutMapping("/{id}")
  public MovieResponse update(@PathVariable UUID id, @Valid @RequestBody MovieRequest req) {
    return MovieResponse.from(svc.update(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    svc.delete(id);
    return ResponseEntity.noContent().build();
  }
}
