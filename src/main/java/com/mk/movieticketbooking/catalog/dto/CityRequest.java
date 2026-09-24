package com.mk.movieticketbooking.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 120) String state) {}
