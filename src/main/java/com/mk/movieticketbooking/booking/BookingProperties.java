package com.mk.movieticketbooking.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds {@code app.booking.*}. The hold TTL controls how long a PENDING
 * booking's seats stay HELD before the sweeper releases them.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(int holdTtlMinutes, long holdSweepIntervalMs) {

  public Duration holdTtl() {
    return Duration.ofMinutes(holdTtlMinutes);
  }
}
