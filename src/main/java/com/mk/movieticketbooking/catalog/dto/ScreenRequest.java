package com.mk.movieticketbooking.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScreenRequest(@NotBlank @Size(max = 80) String name) {}
