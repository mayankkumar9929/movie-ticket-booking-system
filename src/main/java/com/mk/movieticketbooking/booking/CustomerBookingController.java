package com.mk.movieticketbooking.booking;

import com.mk.movieticketbooking.booking.dto.BookingResponse;
import com.mk.movieticketbooking.booking.dto.ConfirmRequest;
import com.mk.movieticketbooking.booking.dto.HoldRequest;
import com.mk.movieticketbooking.booking.dto.PaymentResponse;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Customer-facing booking endpoints. Auth required; a caller can only
 * see their own bookings unless they hold {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class CustomerBookingController {

  private final BookingService bookingService;

  public CustomerBookingController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping("/hold")
  public ResponseEntity<BookingResponse> hold(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody HoldRequest req) {
    var result = bookingService.hold(userId, req.showId(), req.showSeatIds());
    var body = BookingResponse.from(result.booking(), result.seats());
    return ResponseEntity
        .created(URI.create("/api/v1/bookings/" + body.id()))
        .body(body);
  }

  @PostMapping("/{id}/confirm")
  public ConfirmResponseBody confirm(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody ConfirmRequest req) {
    var result = bookingService.confirm(userId, id, req.method(), req.instrument());
    return new ConfirmResponseBody(
        BookingResponse.from(result.booking(), result.seats()),
        PaymentResponse.from(result.payment()));
  }

  @GetMapping("/{id}")
  public BookingResponse get(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    var result = bookingService.get(id);
    if (!isAdmin() && !result.booking().getUserId().equals(userId)) {
      // 404 rather than 403 so we don't leak the existence of other users' bookings.
      throw NotFoundException.of("Booking", id);
    }
    return BookingResponse.from(result.booking(), result.seats());
  }

  @GetMapping("/me")
  public List<BookingResponse> mine(@AuthenticationPrincipal UUID userId) {
    return bookingService.listForUser(userId).stream()
        .map(b -> BookingResponse.from(b, bookingService.get(b.getId()).seats()))
        .toList();
  }

  private boolean isAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  /** Composite response — the confirmed booking plus its payment record. */
  public record ConfirmResponseBody(BookingResponse booking, PaymentResponse payment) {}
}
