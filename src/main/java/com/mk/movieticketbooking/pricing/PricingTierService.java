package com.mk.movieticketbooking.pricing;

import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import com.mk.movieticketbooking.pricing.dto.PricingTierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class PricingTierService {

  private final PricingTierRepository tiers;

  public PricingTierService(PricingTierRepository tiers) {
    this.tiers = tiers;
  }

  public PricingTier create(PricingTierRequest req) {
    String name = normalize(req.name());
    if (tiers.existsByName(name)) {
      throw new ConflictException("Pricing tier already exists: " + name);
    }
    PricingTier t = PricingTier.builder()
        .id(UUID.randomUUID())
        .name(name)
        .multiplier(req.multiplier())
        .active(req.active())
        .createdAt(Instant.now())
        .build();
    return tiers.save(t);
  }

  @Transactional(readOnly = true)
  public List<PricingTier> list(boolean activeOnly) {
    return activeOnly ? tiers.findByActiveTrueOrderByNameAsc() : tiers.findAllByOrderByNameAsc();
  }

  @Transactional(readOnly = true)
  public PricingTier get(UUID id) {
    return tiers.findById(id).orElseThrow(() -> NotFoundException.of("PricingTier", id));
  }

  public PricingTier update(UUID id, PricingTierRequest req) {
    PricingTier t = get(id);
    String newName = normalize(req.name());
    if (!t.getName().equals(newName) && tiers.existsByName(newName)) {
      throw new ConflictException("Pricing tier already exists: " + newName);
    }
    t.setName(newName);
    t.setMultiplier(req.multiplier());
    t.setActive(req.active());
    return t;
  }

  /**
   * Hard-delete only. Once the Show entity exists, tiers with references
   * will be refused (409) and admins will need to deactivate instead.
   */
  public void delete(UUID id) {
    PricingTier t = get(id);
    tiers.delete(t);
  }

  private String normalize(String name) {
    return name.trim().toUpperCase(Locale.ROOT);
  }
}
