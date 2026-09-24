package com.mk.movieticketbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permit-all security filter chain: disables the Spring Security auto-config's
 * default HTTP Basic + CSRF + session management so REST endpoints are directly
 * reachable. Access control on individual endpoints will be enforced at the
 * method / controller level once role-based rules are introduced.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable());
    return http.build();
  }
}
