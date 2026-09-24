package com.mk.movieticketbooking.catalog.dto;

import com.mk.movieticketbooking.catalog.SeatCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk seat-layout upload. Sent to
 * {@code POST /api/v1/admin/screens/{screenId}/seats:bulk}.
 */
public record SeatBulkRequest(@NotEmpty @Valid List<SeatSpec> seats) {

  public record SeatSpec(
      @NotBlank @Size(max = 8) String rowLabel,
      @Min(1) int seatNumber,
      @NotNull SeatCategory category) {}
}
