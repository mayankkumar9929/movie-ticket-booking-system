package com.mk.movieticketbooking.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A physical seat in a screen. Location is (rowLabel, seatNumber) — e.g.
 * ("A", 5). Uniqueness is enforced per-screen so two seats can't collide.
 * <p>
 * This is the static layout. Per-show booking state lives on {@code ShowSeat}
 * (added later); seats themselves have no booking status.
 */
@Entity
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_screen_position",
        columnNames = {"screen_id", "row_label", "seat_number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Seat {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "screen_id", nullable = false)
  private Screen screen;

  @Column(name = "row_label", nullable = false, length = 8)
  private String rowLabel;

  @Column(name = "seat_number", nullable = false)
  private int seatNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private SeatCategory category;
}
