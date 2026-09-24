package com.mk.movieticketbooking.discount;

import com.mk.movieticketbooking.discount.dto.DiscountRequest;
import com.mk.movieticketbooking.discount.dto.DiscountResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/discounts")
public class AdminDiscountController {

  private final DiscountService discounts;

  public AdminDiscountController(DiscountService discounts) {
    this.discounts = discounts;
  }

  @PostMapping
  public ResponseEntity<DiscountResponse> create(@Valid @RequestBody DiscountRequest req) {
    Discount created = discounts.create(req);
    return ResponseEntity
        .created(URI.create("/api/v1/admin/discounts/" + created.getId()))
        .body(DiscountResponse.from(created));
  }

  @GetMapping
  public List<DiscountResponse> list() {
    return discounts.list().stream().map(DiscountResponse::from).toList();
  }

  @GetMapping("/{id}")
  public DiscountResponse get(@PathVariable UUID id) {
    return DiscountResponse.from(discounts.get(id));
  }

  @PutMapping("/{id}")
  public DiscountResponse update(
      @PathVariable UUID id, @Valid @RequestBody DiscountRequest req) {
    return DiscountResponse.from(discounts.update(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    discounts.delete(id);
    return ResponseEntity.noContent().build();
  }
}
