package com.mk.movieticketbooking.show;

import com.mk.movieticketbooking.show.dto.ShowCreateRequest;
import com.mk.movieticketbooking.show.dto.ShowResponse;
import com.mk.movieticketbooking.show.dto.ShowSeatResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shows")
public class AdminShowController {

  private final ShowService showService;

  public AdminShowController(ShowService showService) {
    this.showService = showService;
  }

  @PostMapping
  public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowCreateRequest req) {
    Show created = showService.create(req);
    return ResponseEntity
        .created(URI.create("/api/v1/admin/shows/" + created.getId()))
        .body(ShowResponse.from(created));
  }

  @GetMapping
  public List<ShowResponse> list(
      @RequestParam(required = false) UUID movieId,
      @RequestParam(required = false) UUID screenId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return showService.list(movieId, screenId, from, to).stream()
        .map(ShowResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public ShowResponse get(@PathVariable UUID id) {
    return ShowResponse.from(showService.get(id));
  }

  @GetMapping("/{id}/seats")
  public List<ShowSeatResponse> seats(@PathVariable UUID id) {
    return showService.listSeats(id).stream()
        .map(ShowSeatResponse::from)
        .toList();
  }

  @PostMapping("/{id}/cancel")
  public ShowResponse cancel(@PathVariable UUID id) {
    return ShowResponse.from(showService.cancel(id));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    showService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
