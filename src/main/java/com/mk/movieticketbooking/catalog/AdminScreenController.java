package com.mk.movieticketbooking.catalog;

import com.mk.movieticketbooking.catalog.dto.ScreenRequest;
import com.mk.movieticketbooking.catalog.dto.ScreenResponse;
import com.mk.movieticketbooking.catalog.dto.SeatBulkRequest;
import com.mk.movieticketbooking.catalog.dto.SeatResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Screens + their seat layout live under the same controller because the URL
 * hierarchy is {@code /admin/theaters/{tid}/screens} and
 * {@code /admin/screens/{sid}/seats:bulk}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminScreenController {

  private final CatalogService svc;

  public AdminScreenController(CatalogService svc) {
    this.svc = svc;
  }

  @PostMapping("/theaters/{theaterId}/screens")
  public ResponseEntity<ScreenResponse> createScreen(
      @PathVariable UUID theaterId, @Valid @RequestBody ScreenRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ScreenResponse.from(svc.createScreen(theaterId, req)));
  }

  @GetMapping("/theaters/{theaterId}/screens")
  public List<ScreenResponse> listScreens(@PathVariable UUID theaterId) {
    return svc.listScreens(theaterId).stream().map(ScreenResponse::from).toList();
  }

  @GetMapping("/screens/{id}")
  public ScreenResponse getScreen(@PathVariable UUID id) {
    return ScreenResponse.from(svc.getScreen(id));
  }

  @PutMapping("/screens/{id}")
  public ScreenResponse updateScreen(
      @PathVariable UUID id, @Valid @RequestBody ScreenRequest req) {
    return ScreenResponse.from(svc.updateScreen(id, req));
  }

  @DeleteMapping("/screens/{id}")
  public ResponseEntity<Void> deleteScreen(@PathVariable UUID id) {
    svc.deleteScreen(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/screens/{screenId}/seats:bulk")
  public ResponseEntity<List<SeatResponse>> createSeats(
      @PathVariable UUID screenId, @Valid @RequestBody SeatBulkRequest req) {
    List<SeatResponse> body =
        svc.createSeats(screenId, req).stream().map(SeatResponse::from).toList();
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  @GetMapping("/screens/{screenId}/seats")
  public List<SeatResponse> listSeats(@PathVariable UUID screenId) {
    return svc.listSeats(screenId).stream().map(SeatResponse::from).toList();
  }

  @DeleteMapping("/seats/{id}")
  public ResponseEntity<Void> deleteSeat(@PathVariable UUID id) {
    svc.deleteSeat(id);
    return ResponseEntity.noContent().build();
  }
}
