package com.mk.movieticketbooking.catalog;

import com.mk.movieticketbooking.catalog.dto.CityRequest;
import com.mk.movieticketbooking.catalog.dto.ScreenRequest;
import com.mk.movieticketbooking.catalog.dto.SeatBulkRequest;
import com.mk.movieticketbooking.catalog.dto.TheaterRequest;
import com.mk.movieticketbooking.common.exception.ConflictException;
import com.mk.movieticketbooking.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin-facing operations over the venue catalog: cities, theaters, screens,
 * and seat layouts.
 * <p>
 * Delete semantics: a row is refused (409) if any child records still exist.
 * Admins must tear down dependents explicitly rather than have deletion
 * silently cascade through shows and bookings.
 */
@Service
@Transactional
public class CatalogService {

  private final CityRepository cities;
  private final TheaterRepository theaters;
  private final ScreenRepository screens;
  private final SeatRepository seats;

  public CatalogService(
      CityRepository cities,
      TheaterRepository theaters,
      ScreenRepository screens,
      SeatRepository seats) {
    this.cities = cities;
    this.theaters = theaters;
    this.screens = screens;
    this.seats = seats;
  }

  // -------------------- Cities --------------------

  public City createCity(CityRequest req) {
    if (cities.existsByNameAndState(req.name(), req.state())) {
      throw new ConflictException("City already exists: " + req.name() + ", " + req.state());
    }
    City c = City.builder()
        .id(UUID.randomUUID())
        .name(req.name())
        .state(req.state())
        .createdAt(Instant.now())
        .build();
    return cities.save(c);
  }

  @Transactional(readOnly = true)
  public List<City> listCities() {
    return cities.findAll();
  }

  @Transactional(readOnly = true)
  public City getCity(UUID id) {
    return cities.findById(id).orElseThrow(() -> NotFoundException.of("City", id));
  }

  public City updateCity(UUID id, CityRequest req) {
    City c = getCity(id);
    if (!c.getName().equals(req.name()) || !c.getState().equals(req.state())) {
      if (cities.existsByNameAndState(req.name(), req.state())) {
        throw new ConflictException("City already exists: " + req.name() + ", " + req.state());
      }
    }
    c.setName(req.name());
    c.setState(req.state());
    return c;
  }

  public void deleteCity(UUID id) {
    City c = getCity(id);
    if (theaters.existsByCityId(id)) {
      throw new ConflictException("City has theaters; delete those first");
    }
    cities.delete(c);
  }

  // -------------------- Theaters --------------------

  public Theater createTheater(TheaterRequest req) {
    City city = getCity(req.cityId());
    Theater t = Theater.builder()
        .id(UUID.randomUUID())
        .name(req.name())
        .address(req.address())
        .city(city)
        .createdAt(Instant.now())
        .build();
    return theaters.save(t);
  }

  @Transactional(readOnly = true)
  public List<Theater> listTheaters(UUID cityId) {
    return cityId == null ? theaters.findAll() : theaters.findByCityId(cityId);
  }

  @Transactional(readOnly = true)
  public Theater getTheater(UUID id) {
    return theaters.findById(id).orElseThrow(() -> NotFoundException.of("Theater", id));
  }

  public Theater updateTheater(UUID id, TheaterRequest req) {
    Theater t = getTheater(id);
    t.setName(req.name());
    t.setAddress(req.address());
    if (!t.getCity().getId().equals(req.cityId())) {
      t.setCity(getCity(req.cityId()));
    }
    return t;
  }

  public void deleteTheater(UUID id) {
    Theater t = getTheater(id);
    if (screens.existsByTheaterId(id)) {
      throw new ConflictException("Theater has screens; delete those first");
    }
    theaters.delete(t);
  }

  // -------------------- Screens --------------------

  public Screen createScreen(UUID theaterId, ScreenRequest req) {
    Theater theater = getTheater(theaterId);
    Screen s = Screen.builder()
        .id(UUID.randomUUID())
        .name(req.name())
        .theater(theater)
        .createdAt(Instant.now())
        .build();
    return screens.save(s);
  }

  @Transactional(readOnly = true)
  public List<Screen> listScreens(UUID theaterId) {
    // touch the theater so a bad id 404s rather than returning []
    getTheater(theaterId);
    return screens.findByTheaterId(theaterId);
  }

  @Transactional(readOnly = true)
  public Screen getScreen(UUID id) {
    return screens.findById(id).orElseThrow(() -> NotFoundException.of("Screen", id));
  }

  public Screen updateScreen(UUID id, ScreenRequest req) {
    Screen s = getScreen(id);
    s.setName(req.name());
    return s;
  }

  public void deleteScreen(UUID id) {
    Screen s = getScreen(id);
    if (seats.existsByScreenId(id)) {
      throw new ConflictException("Screen has a seat layout; delete seats first");
    }
    screens.delete(s);
  }

  // -------------------- Seats --------------------

  /**
   * Bulk-upload a seat layout for a screen. All-or-nothing: if any spec
   * collides with an existing seat (same row/number) the whole request fails
   * without partial writes.
   */
  public List<Seat> createSeats(UUID screenId, SeatBulkRequest req) {
    Screen screen = getScreen(screenId);
    List<Seat> existing = seats.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screenId);
    var taken = existing.stream()
        .map(s -> s.getRowLabel() + "-" + s.getSeatNumber())
        .collect(java.util.stream.Collectors.toSet());

    List<Seat> toSave = new java.util.ArrayList<>(req.seats().size());
    var seenInRequest = new java.util.HashSet<String>();
    for (var spec : req.seats()) {
      String key = spec.rowLabel() + "-" + spec.seatNumber();
      if (!seenInRequest.add(key)) {
        throw new ConflictException("Duplicate seat in request: " + key);
      }
      if (taken.contains(key)) {
        throw new ConflictException("Seat already exists on screen: " + key);
      }
      toSave.add(Seat.builder()
          .id(UUID.randomUUID())
          .screen(screen)
          .rowLabel(spec.rowLabel())
          .seatNumber(spec.seatNumber())
          .category(spec.category())
          .build());
    }
    return seats.saveAll(toSave);
  }

  @Transactional(readOnly = true)
  public List<Seat> listSeats(UUID screenId) {
    getScreen(screenId);
    return seats.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screenId);
  }

  public void deleteSeat(UUID id) {
    Seat s = seats.findById(id).orElseThrow(() -> NotFoundException.of("Seat", id));
    seats.delete(s);
  }
}
