package com.mk.movieticketbooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {

  boolean existsByNameAndState(String name, String state);
}
