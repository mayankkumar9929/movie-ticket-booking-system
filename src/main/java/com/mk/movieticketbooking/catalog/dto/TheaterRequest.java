package com.mk.movieticketbooking.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TheaterRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @NotNull UUID cityId) {}
