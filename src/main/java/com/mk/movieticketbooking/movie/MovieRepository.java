package com.mk.movieticketbooking.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

  List<Movie> findByTitleContainingIgnoreCaseOrderByTitleAsc(String titleFragment);
}
