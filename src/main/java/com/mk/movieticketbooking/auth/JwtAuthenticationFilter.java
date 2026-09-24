package com.mk.movieticketbooking.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Extracts a bearer JWT from the {@code Authorization} header, verifies it,
 * and populates the security context with a {@link UsernamePasswordAuthenticationToken}
 * whose principal is the user id (UUID) and whose authority is {@code ROLE_<role>}.
 * <p>
 * On any parse / signature / expiry failure the context is left empty and
 * the request continues anonymously — the downstream rules decide whether
 * that's a 401 or a permitted call.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String HEADER = "Authorization";
  private static final String PREFIX = "Bearer ";

  private final JwtService jwt;

  public JwtAuthenticationFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(HEADER);
    if (header != null && header.startsWith(PREFIX)) {
      String token = header.substring(PREFIX.length());
      try {
        Claims claims = jwt.parse(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get(JwtService.CLAIM_ROLE, String.class);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException | IllegalArgumentException ex) {
        // Bad / expired / malformed token → stay anonymous. Log at debug because
        // this is a client mistake, not a server problem.
        log.debug("Rejected JWT: {}", ex.getMessage());
        SecurityContextHolder.clearContext();
      }
    }

    chain.doFilter(request, response);
  }
}
