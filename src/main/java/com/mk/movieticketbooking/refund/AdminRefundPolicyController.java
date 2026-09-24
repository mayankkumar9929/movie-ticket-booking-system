package com.mk.movieticketbooking.refund;

import com.mk.movieticketbooking.refund.dto.RefundPolicyTierRequest;
import com.mk.movieticketbooking.refund.dto.RefundPolicyTierResponse;
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
@RequestMapping("/api/v1/admin/refund-policy")
public class AdminRefundPolicyController {

  private final RefundPolicyService policy;

  public AdminRefundPolicyController(RefundPolicyService policy) {
    this.policy = policy;
  }

  @PostMapping("/tiers")
  public ResponseEntity<RefundPolicyTierResponse> create(
      @Valid @RequestBody RefundPolicyTierRequest req) {
    RefundPolicyTier created = policy.create(req);
    return ResponseEntity
        .created(URI.create("/api/v1/admin/refund-policy/tiers/" + created.getId()))
        .body(RefundPolicyTierResponse.from(created));
  }

  @GetMapping("/tiers")
  public List<RefundPolicyTierResponse> list() {
    return policy.list().stream().map(RefundPolicyTierResponse::from).toList();
  }

  @GetMapping("/tiers/{id}")
  public RefundPolicyTierResponse get(@PathVariable UUID id) {
    return RefundPolicyTierResponse.from(policy.get(id));
  }

  @PutMapping("/tiers/{id}")
  public RefundPolicyTierResponse update(
      @PathVariable UUID id, @Valid @RequestBody RefundPolicyTierRequest req) {
    return RefundPolicyTierResponse.from(policy.update(id, req));
  }

  @DeleteMapping("/tiers/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    policy.delete(id);
    return ResponseEntity.noContent().build();
  }
}
