package com.mk.movieticketbooking.catalog;

import com.mk.movieticketbooking.catalog.dto.CityRequest;
import com.mk.movieticketbooking.catalog.dto.CityResponse;
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

@RestController
@RequestMapping("/api/v1/admin/cities")
public class AdminCityController {

  private final CatalogService svc;

  public AdminCityController(CatalogService svc) {
    this.svc = svc;
  }

  @PostMapping
  public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(CityResponse.from(svc.createCity(req)));
  }

  @GetMapping
  public List<CityResponse> list() {
    return svc.listCities().stream().map(CityResponse::from).toList();
  }

  @GetMapping("/{id}")
  public CityResponse get(@PathVariable UUID id) {
    return CityResponse.from(svc.getCity(id));
  }

  @PutMapping("/{id}")
  public CityResponse update(@PathVariable UUID id, @Valid @RequestBody CityRequest req) {
    return CityResponse.from(svc.updateCity(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    svc.deleteCity(id);
    return ResponseEntity.noContent().build();
  }
}
