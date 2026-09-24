package com.mk.movieticketbooking.pricing;

import com.mk.movieticketbooking.pricing.dto.PricingTierRequest;
import com.mk.movieticketbooking.pricing.dto.PricingTierResponse;
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
@RequestMapping("/api/v1/admin/pricing-tiers")
public class AdminPricingTierController {

  private final PricingTierService svc;

  public AdminPricingTierController(PricingTierService svc) {
    this.svc = svc;
  }

  @PostMapping
  public ResponseEntity<PricingTierResponse> create(@Valid @RequestBody PricingTierRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(PricingTierResponse.from(svc.create(req)));
  }

  @GetMapping
  public List<PricingTierResponse> list(
      @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
    return svc.list(activeOnly).stream().map(PricingTierResponse::from).toList();
  }

  @GetMapping("/{id}")
  public PricingTierResponse get(@PathVariable UUID id) {
    return PricingTierResponse.from(svc.get(id));
  }

  @PutMapping("/{id}")
  public PricingTierResponse update(
      @PathVariable UUID id, @Valid @RequestBody PricingTierRequest req) {
    return PricingTierResponse.from(svc.update(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    svc.delete(id);
    return ResponseEntity.noContent().build();
  }
}
