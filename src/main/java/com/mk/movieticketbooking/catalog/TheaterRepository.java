package com.mk.movieticketbooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TheaterRepository extends JpaRepository<Theater, UUID> {

  List<Theater> findByCityId(UUID cityId);

  boolean existsByCityId(UUID cityId);
}
