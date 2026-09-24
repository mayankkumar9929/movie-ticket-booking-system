package com.mk.movieticketbooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request. All new accounts start as CUSTOMER.
 * Admin accounts are created out-of-band (seed / dedicated admin script).
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 120) String name) {}
