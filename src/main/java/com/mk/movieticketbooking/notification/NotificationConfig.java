package com.mk.movieticketbooking.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures the {@code notificationExecutor} used by
 * {@link BookingEventListener}. A dedicated bounded pool with a caller-
 * runs fallback keeps slow notification sinks from starving other async
 * work if we add any later.
 */
@Configuration
@EnableAsync
public class NotificationConfig {

  @Bean("notificationExecutor")
  public TaskExecutor notificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("notify-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return executor;
  }
}
