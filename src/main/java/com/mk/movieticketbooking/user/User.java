package com.mk.movieticketbooking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A system user. Identified by a UUID so ids are safe to expose in URLs and
 * stable across environments (no auto-increment coupling to insertion order).
 * <p>
 * Email is the login handle and enforced unique at the DB level.
 * Password is stored only as a BCrypt hash — the plaintext never touches
 * persistence.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  /** BCrypt hash. Never store plaintext. */
  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
