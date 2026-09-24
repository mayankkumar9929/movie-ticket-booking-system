package com.mk.movieticketbooking.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PricingTierRepository extends JpaRepository<PricingTier, UUID> {

  boolean existsByName(String name);

  List<PricingTier> findByActiveTrueOrderByNameAsc();

  List<PricingTier> findAllByOrderByNameAsc();
}
