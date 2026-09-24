package com.mk.movieticketbooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreenRepository extends JpaRepository<Screen, UUID> {

  List<Screen> findByTheaterId(UUID theaterId);

  boolean existsByTheaterId(UUID theaterId);
}
