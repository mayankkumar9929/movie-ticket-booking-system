package com.mk.movieticketbooking.catalog;

import com.mk.movieticketbooking.catalog.dto.TheaterRequest;
import com.mk.movieticketbooking.catalog.dto.TheaterResponse;
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
@RequestMapping("/api/v1/admin/theaters")
public class AdminTheaterController {

  private final CatalogService svc;

  public AdminTheaterController(CatalogService svc) {
    this.svc = svc;
  }

  @PostMapping
  public ResponseEntity<TheaterResponse> create(@Valid @RequestBody TheaterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(TheaterResponse.from(svc.createTheater(req)));
  }

  @GetMapping
  public List<TheaterResponse> list(@RequestParam(required = false) UUID cityId) {
    return svc.listTheaters(cityId).stream().map(TheaterResponse::from).toList();
  }

  @GetMapping("/{id}")
  public TheaterResponse get(@PathVariable UUID id) {
    return TheaterResponse.from(svc.getTheater(id));
  }

  @PutMapping("/{id}")
  public TheaterResponse update(@PathVariable UUID id, @Valid @RequestBody TheaterRequest req) {
    return TheaterResponse.from(svc.updateTheater(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    svc.deleteTheater(id);
    return ResponseEntity.noContent().build();
  }
}
