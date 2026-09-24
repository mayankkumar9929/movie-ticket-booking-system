package com.mk.movieticketbooking.notification;

import java.util.UUID;

/**
 * Delivers a notification to a user. The real impl would send email /
 * SMS / push; the default {@link LoggingNotificationSender} just writes
 * a structured line to the log.
 */
public interface NotificationSender {

  void send(UUID userId, String subject, String body);
}
