package com.mk.movieticketbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default notification sink: emits a structured log line. Swappable for
 * a real email / SMS gateway by providing another {@link NotificationSender}
 * bean.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(UUID userId, String subject, String body) {
    log.info("Notification to user={} subject=\"{}\" body=\"{}\"", userId, subject, body);
  }
}
