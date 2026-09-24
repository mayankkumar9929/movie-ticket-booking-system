package com.mk.movieticketbooking.movie.dto;

import com.mk.movieticketbooking.movie.MovieRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovieRequest(
    @NotBlank @Size(max = 200) String title,
    @Min(1) @Max(600) int durationMinutes,
    @NotBlank @Size(max = 40) String language,
    @NotNull MovieRating rating,
    @Size(max = 2000) String synopsis) {}
