package com.mk.movieticketbooking.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds {@code app.notification.*}. Controls when and how often the
 * pre-show reminder scheduler runs.
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
    int reminderLookaheadMinutes,
    long reminderSweepIntervalMs) {

  public Duration reminderLookahead() {
    return Duration.ofMinutes(reminderLookaheadMinutes);
  }
}
