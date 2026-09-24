package com.mk.movieticketbooking.auth.dto;

import com.mk.movieticketbooking.user.Role;

import java.util.UUID;

/**
 * Returned on successful register or login.
 * Callers put {@code token} in the {@code Authorization: Bearer ...} header.
 */
public record AuthResponse(
    UUID userId,
    String email,
    String name,
    Role role,
    String token,
    long expiresInSeconds) {}
