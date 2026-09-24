package com.mk.movieticketbooking.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundPolicyTierRepository extends JpaRepository<RefundPolicyTier, UUID> {

  List<RefundPolicyTier> findAllByOrderByMinHoursBeforeShowDesc();
}
