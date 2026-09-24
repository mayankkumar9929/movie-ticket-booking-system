package com.mk.movieticketbooking.auth;

import com.mk.movieticketbooking.auth.dto.AuthResponse;
import com.mk.movieticketbooking.auth.dto.LoginRequest;
import com.mk.movieticketbooking.auth.dto.RegisterRequest;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.user.Role;
import com.mk.movieticketbooking.user.User;
import com.mk.movieticketbooking.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles account creation and credential-based token issuance.
 * <p>
 * New signups default to {@link Role#CUSTOMER}. Admin accounts must be created
 * through a separate seed / bootstrap mechanism (see README) — a public
 * "register as admin" endpoint would be a trivial privilege-escalation hole.
 */
@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwt;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwt = jwt;
  }

  @Transactional
  public AuthResponse register(RegisterRequest req) {
    String email = req.email().toLowerCase();
    if (users.existsByEmail(email)) {
      throw new ConflictException("Email already registered");
    }
    User u = User.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordEncoder.encode(req.password()))
        .name(req.name())
        .role(Role.CUSTOMER)
        .createdAt(Instant.now())
        .build();
    users.save(u);
    return toResponse(u);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest req) {
    String email = req.email().toLowerCase();
    // Deliberately use the same message for "no such user" and "wrong password"
    // so an attacker can't enumerate registered emails.
    User u = users.findByEmail(email)
        .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }
    return toResponse(u);
  }

  private AuthResponse toResponse(User u) {
    String token = jwt.issue(u.getId(), u.getEmail(), u.getRole());
    return new AuthResponse(
        u.getId(), u.getEmail(), u.getName(), u.getRole(), token, jwt.expirationSeconds());
  }
}
