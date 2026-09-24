package com.mk.movieticketbooking.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A city in which theaters can operate.
 * Uniqueness is enforced on (name, state) so "Springfield, IL" and
 * "Springfield, MA" are distinct rows.
 */
@Entity
@Table(
    name = "cities",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_city_name_state",
        columnNames = {"name", "state"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class City {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "state", nullable = false, length = 120)
  private String state;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
