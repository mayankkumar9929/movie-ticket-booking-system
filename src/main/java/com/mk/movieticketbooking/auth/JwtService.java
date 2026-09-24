package com.mk.movieticketbooking.auth;

import com.mk.movieticketbooking.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies HS256 JWTs.
 * <p>
 * Claims layout:
 * <ul>
 *   <li>{@code sub}: user id (UUID as string)</li>
 *   <li>{@code email}: login handle</li>
 *   <li>{@code role}: {@link Role} name</li>
 *   <li>{@code iat} / {@code exp}: standard timestamps</li>
 * </ul>
 * <p>
 * The secret is read from {@code app.security.jwt.secret}. It must be long
 * enough for HS256 (at least 32 bytes / 256 bits after UTF-8 encoding); the
 * jjwt library will refuse a shorter one.
 */
@Service
public class JwtService {

  static final String CLAIM_EMAIL = "email";
  static final String CLAIM_ROLE = "role";

  private final String secret;
  private final Duration expiration;
  private SecretKey signingKey;

  public JwtService(
      @Value("${app.security.jwt.secret}") String secret,
      @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
    this.secret = secret;
    this.expiration = Duration.ofMinutes(expirationMinutes);
  }

  @PostConstruct
  void init() {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String issue(UUID userId, String email, Role role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_ROLE, role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Parses and verifies signature + expiry. Throws {@link io.jsonwebtoken.JwtException}
   * subclasses on any failure (bad signature, expired, malformed, etc.).
   */
  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public long expirationSeconds() {
    return expiration.toSeconds();
  }
}
