package com.mk.movieticketbooking.config;

import com.mk.movieticketbooking.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT-based security.
 * <p>
 * Route rules:
 * <ul>
 *   <li>{@code /api/v1/auth/**} — public (register, login)</li>
 *   <li>{@code /api/v1/public/**} — public (browse catalog and shows)</li>
 *   <li>{@code /h2-console/**} — public in dev; disable or restrict for prod</li>
 *   <li>{@code /error} — public so error responses render without recursion</li>
 *   <li>{@code /api/v1/admin/**} — {@code ROLE_ADMIN}</li>
 *   <li>everything else — authenticated</li>
 * </ul>
 * Method-level {@code @PreAuthorize} is enabled for finer-grained checks
 * (e.g. "customer can only cancel their own booking").
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final AuthenticationEntryPoint entryPoint;
  private final AccessDeniedHandler accessDeniedHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtFilter,
      AuthenticationEntryPoint entryPoint,
      AccessDeniedHandler accessDeniedHandler) {
    this.jwtFilter = jwtFilter;
    this.entryPoint = entryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/api/v1/public/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        // H2 console renders inside a frame; allow same-origin framing for dev.
        .headers(h -> h.frameOptions(f -> f.sameOrigin()))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
