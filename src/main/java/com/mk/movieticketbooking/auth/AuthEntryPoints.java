package com.mk.movieticketbooking.auth;

import com.mk.movieticketbooking.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Renders authentication (401) and authorization (403) failures using the
 * same {@link ApiError} envelope produced by {@code GlobalExceptionHandler},
 * so clients see one consistent error shape regardless of layer.
 */
@Configuration
public class AuthEntryPoints {

  @Bean
  public AuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper mapper) {
    return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException ex) ->
        write(mapper, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "Authentication required", req.getRequestURI());
  }

  @Bean
  public AccessDeniedHandler restAccessDeniedHandler(ObjectMapper mapper) {
    return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.access.AccessDeniedException ex) ->
        write(mapper, res, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "You do not have permission to access this resource", req.getRequestURI());
  }

  private void write(ObjectMapper mapper, HttpServletResponse res, HttpStatus status,
                     String code, String message, String path) throws java.io.IOException {
    ApiError body = new ApiError(
        Instant.now(),
        status.value(),
        code,
        message,
        path,
        UUID.randomUUID().toString(),
        null);
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(res.getOutputStream(), body);
  }
}
