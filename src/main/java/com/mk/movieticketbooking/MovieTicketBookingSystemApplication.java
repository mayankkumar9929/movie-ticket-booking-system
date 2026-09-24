package com.mk.movieticketbooking;

import com.mk.movieticketbooking.booking.BookingProperties;
import com.mk.movieticketbooking.show.CategorySurchargeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({CategorySurchargeConfig.class, BookingProperties.class})
@EnableScheduling
public class MovieTicketBookingSystemApplication {

  public static void main(String[] args) {
    SpringApplication.run(MovieTicketBookingSystemApplication.class, args);
  }

}
