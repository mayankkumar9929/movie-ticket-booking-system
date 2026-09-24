# Architecture Plan — Movie Ticket Booking System

> Design document. Nothing here is code yet — this is what we're going to build and why. Every meaningful decision is documented as an **Assumption** so it can flow into the README.

---

## 1. Scope

### In (will build)
- **Admin flows**: manage cities, theaters, screens, seat layouts, shows, pricing tiers, discount codes, refund policies.
- **Customer flows**: browse cities → theaters → shows → seat map; hold seats (time-bound); pay (mock gateway); confirm booking; view history; cancel with refund.
- **System flows**: expire holds automatically; send confirmation & reminder notifications asynchronously.
- **Auth**: Spring Security with two roles (`ADMIN`, `CUSTOMER`), JWT-based.
- **Persistence**: H2 (in-memory for tests, file-based for dev).
- **Validation & error handling**: bean validation + a global `@ControllerAdvice`.
- **Tests**: unit tests for services + integration tests (`@SpringBootTest`) for core flows.

### Out (explicit non-goals from problem statement)
- No UI, no containerization, no CI/CD, no OAuth/SSO/MFA, no microservices, no real payment integration, no production observability.

### Runtime target
**Local-only, single-JVM.** Runs on a developer machine — no cloud deployment, no multi-instance / horizontally-scaled setup. Concurrency across many simultaneous users on this one JVM is still fully in scope (Tomcat's request threads race on `ShowSeat` rows; optimistic locking serializes them). What we skip because we're single-instance:
- H2 in-memory / file DB is acceptable — no need for Postgres, Flyway migrations, or Testcontainers.
- JWT signing key lives in `application.yaml`; env-var injection is not wired up.
- `@Scheduled` runs in-process on one node; no distributed lock coordination needed.
- Async notifications use in-process `@Async` + `ApplicationEventPublisher`; no external broker.
- CORS, HTTPS termination, readiness/liveness probes, metrics endpoints are out of scope.
- H2 console is left enabled at `/h2-console` for inspection.

---

## 2. Domain Model

### Entities

| Entity | Purpose | Key fields |
|---|---|---|
| `User` | Actor | `id`, `email` (unique), `passwordHash`, `role` (`ADMIN` \| `CUSTOMER`), `name` |
| `City` | Admin-managed | `id`, `name`, `state` |
| `Theater` | Admin-managed, belongs to city | `id`, `name`, `address`, `cityId` |
| `Screen` | A hall inside a theater | `id`, `name`, `theaterId` |
| `Seat` | Physical seat in a screen (the layout) | `id`, `screenId`, `row`, `number`, `category` (STANDARD/PREMIUM/RECLINER) |
| `Movie` | Catalog | `id`, `title`, `durationMinutes`, `language`, `rating`, `synopsis` |
| `Show` | A scheduled screening | `id`, `movieId`, `screenId`, `startsAt`, `endsAt`, `basePriceTierId` |
| `PricingTier` | Named price levels | `id`, `name` (REGULAR/PREMIUM/WEEKEND), `multiplier` (Decimal) |
| **`ShowSeat`** | **Per-show seat state** ⚠️ concurrency anchor | `id`, `showId`, `seatId`, `status` (AVAILABLE/HELD/BOOKED), `heldUntil`, `holdBookingId`, `priceAtShow` (Decimal), `@Version` |
| `DiscountCode` | Promotion | `id`, `code` (unique), `percentOff`, `maxDiscount`, `validFrom`, `validTo`, `active` |
| `Booking` | A customer's booking (may be pending/confirmed/cancelled) | `id`, `userId`, `showId`, `status` (PENDING/CONFIRMED/CANCELLED/EXPIRED), `totalAmount`, `discountAmount`, `createdAt`, `expiresAt`, `discountCodeId?` |
| `BookingSeat` | Join: which seats a booking claims | `id`, `bookingId`, `showSeatId`, `pricePaid` |
| `Payment` | Payment record | `id`, `bookingId`, `amount`, `status` (SUCCESS/FAILED/REFUNDED), `gatewayRef`, `paidAt` |
| `RefundPolicy` | Configurable refund rules | `id`, `name`, `active`, `rules` (list of `{ hoursBeforeShow, refundPercent }`) |
| `Notification` | Audit of outgoing notifications | `id`, `userId`, `bookingId`, `type` (CONFIRMATION/REMINDER/CANCELLATION), `channel` (EMAIL/SMS — logged only), `status`, `sentAt` |

### Design decisions & rationale

**A1. `ShowSeat` snapshot per show is the concurrency anchor.**
When a show is created, we materialize a `ShowSeat` row for every physical `Seat` in the screen. All hold/booking logic operates on `ShowSeat`, never on `Seat` directly. This gives us:
- A single row per (show, seat) that can be locked and versioned.
- A place to store the price actually charged at booking time (so retroactive price changes don't affect issued bookings).
- Isolation between concurrent shows in the same screen.

**A2. Price is captured at hold-time on `BookingSeat.pricePaid`.**
Prevents surprises if an admin edits a `PricingTier` mid-flight.

**A3. Weekend pricing is derived, not stored.**
`WEEKEND` tier is applied by rule at show-creation time (if `startsAt` is Sat/Sun, use WEEKEND multiplier). Admin can override per show.

**A4. Refund policies are attached at booking-time, not looked up dynamically.**
Store `refundPolicyId` on `Booking` at confirmation so a policy edit doesn't retroactively change refunds owed.

---

## 3. Concurrency — the hard part

**Problem:** Two customers click "book seat A5" at the same instant. Exactly one must win.

**Chosen approach: JPA optimistic locking + unique partial constraint.**

```
ShowSeat {
  @Version Long version;
  status ∈ {AVAILABLE, HELD, BOOKED}
  heldUntil TIMESTAMP NULL
  holdBookingId UUID NULL
}
```

Hold flow (per seat, within one transaction):
1. `SELECT ... FROM show_seat WHERE id = ? AND status = 'AVAILABLE'` (or `status='HELD' AND heldUntil < NOW()`).
2. `UPDATE ... SET status='HELD', heldUntil=?, holdBookingId=?, version=version+1 WHERE id=? AND version=?`.
3. If update affects 0 rows → `SeatUnavailableException` → whole booking aborts (rollback), user retries.

We also add a **unique constraint on `(show_seat_id, status)` where status='BOOKED'** — belt-and-braces — so even under pathological races, the DB refuses a second BOOKED row for the same seat. (Actually we enforce this via a unique constraint on `booking_seat.show_seat_id` when the parent booking is CONFIRMED — implemented via check or by making show_seat status transition the single source of truth. Simplest: enforce via `ShowSeat.status` transition atomicity described above; the join table doesn't need the constraint because ShowSeat is the anchor.)

**A5. Hold TTL: 5 minutes (configurable via `booking.hold-ttl-minutes`).**

**A6. Hold expiry:** a `@Scheduled` job every 30 seconds finds `ShowSeat.status='HELD' AND heldUntil < NOW()` and flips them back to `AVAILABLE`, and marks the parent `Booking` as `EXPIRED`. Idempotent.

**A7. All seats in one booking are held in a single transaction.** If any seat is unavailable, the whole booking fails — no partial holds. Seat IDs are sorted before locking to prevent deadlocks between concurrent multi-seat bookings.

---

## 4. Notifications (non-blocking)

**A8. In-process async via `@Async` + `ApplicationEventPublisher`.**

- `BookingConfirmedEvent` published inside the confirm-booking transaction (via `TransactionalEventListener(phase=AFTER_COMMIT)`).
- Async listener writes a `Notification` row and logs the "sent" event. No real email/SMS integration — a `NotificationSender` interface with a `LoggingNotificationSender` impl.
- Reminders: a `@Scheduled` job every 5 minutes finds confirmed bookings whose show starts within the next hour and hasn't been reminded yet.

Meets "delivered without blocking the booking flow" without pulling in Kafka/RabbitMQ.

---

## 5. Pricing & discounts

Order of operations at booking confirm:
1. Base price per seat = `PricingTier.multiplier × basePrice` (from Show).
2. Optional per-seat category surcharge (PREMIUM/RECLINER) — stored on `Seat.category` × configurable per-category multiplier.
3. Sum per-seat prices → `subtotal`.
4. Apply `DiscountCode` if provided: `discountAmount = min(subtotal × percentOff / 100, maxDiscount)`.
5. `totalAmount = subtotal - discountAmount`.

**A9. Discount codes have per-user and global usage limits — for scope, we implement global `active` + validity window only, no per-user redemption tracking.** (Documented as a known limitation.)

---

## 6. Payment (mock)

`PaymentGateway` interface with `charge(amount, bookingId)` returning `PaymentResult`. `MockPaymentGateway` succeeds if `amount > 0` unless a magic amount `13.13` is used (test hook for failure path). Refund is also mocked.

---

## 7. API surface

Base path: `/api/v1`.

### Public / auth
- `POST /auth/register` — customer signup
- `POST /auth/login` — returns JWT

### Admin (`ROLE_ADMIN`)
- `POST /admin/cities`, `GET/PUT/DELETE /admin/cities/{id}`
- `POST /admin/theaters`, ...
- `POST /admin/screens/{id}/seats:bulk` — create seat layout
- `POST /admin/movies`, ...
- `POST /admin/shows` — creates show + materializes ShowSeats
- `POST /admin/pricing-tiers`, `POST /admin/discount-codes`, `POST /admin/refund-policies`

### Customer (`ROLE_CUSTOMER`)
- `GET /cities` — list cities
- `GET /cities/{id}/theaters`
- `GET /shows?cityId=&movieId=&date=` — browse
- `GET /shows/{id}/seats` — seat map with per-seat status & price
- `POST /bookings` — body: `{ showId, seatIds[], discountCode? }` → creates PENDING booking + holds seats, returns holdExpiresAt
- `POST /bookings/{id}/confirm` — body: `{ paymentToken }` → charges + confirms
- `POST /bookings/{id}/cancel` → refunds per policy
- `GET /me/bookings` — booking history

### Error model
Consistent JSON: `{ "timestamp", "status", "error", "message", "path", "traceId" }` via `@RestControllerAdvice`.

---

## 8. Security

- Spring Security 6 filter chain, stateless.
- JWT (HS256) with 24h expiry. Secret in `application.yaml` (dev only — noted as assumption).
- Passwords: BCrypt.
- Method security via `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints.
- `@AuthenticationPrincipal` on customer endpoints — customers can only see/mutate their own bookings.

**A10. No refresh tokens, no password reset, no email verification.** Out of scope per problem statement.

---

## 9. Package layout

```
com.mk.movieticketbooking
├── MovieTicketBookingSystemApplication.java
├── config/           # SecurityConfig, AsyncConfig, JacksonConfig, OpenApiConfig
├── common/           # error handling, exceptions, base types
├── auth/             # controller, service, JwtService, dto
├── user/             # entity, repo
├── catalog/          # City, Theater, Screen, Seat, Movie — entity/repo/service/controller
├── show/             # Show, ShowSeat — including seat materialization
├── pricing/          # PricingTier, DiscountCode
├── booking/          # Booking, BookingSeat, BookingService (hold/confirm/cancel), scheduler
├── payment/          # PaymentGateway iface + MockPaymentGateway
├── refund/           # RefundPolicy + refund calculator
└── notification/     # events, listeners, NotificationSender, scheduler
```

Each module: `entity/`, `repository/`, `service/`, `controller/`, `dto/`, `mapper/`.

---

## 10. Testing strategy

- **Unit tests** (JUnit 5 + Mockito): pricing calculator, refund calculator, JWT service, hold-expiry logic.
- **Integration tests** (`@SpringBootTest` + `MockMvc` + H2): end-to-end for each core flow — signup, admin creates a show, customer browses and books, cancel + refund.
- **Concurrency test**: spin up N threads all trying to book the same seat; assert exactly one succeeds. This is the marquee test.
- Test data seeded via a `TestDataBuilder`, not SQL scripts.

---

## 11. Dev workflow — commit-by-commit plan

Each bullet is intended as one commit (roughly):

1. `chore: bootstrap pom.xml with web, data-jpa, validation, security, h2, jwt, lombok`
2. `feat: base error handling + global exception advice`
3. `feat: user + auth (register/login) with JWT and BCrypt`
4. `feat: security config with role-based access`
5. `feat: catalog — city, theater, screen, seat + admin CRUD`
6. `feat: movie catalog + admin CRUD`
7. `feat: pricing tiers + admin CRUD`
8. `feat: show creation with automatic ShowSeat materialization`
9. `feat: customer browse endpoints (cities, theaters, shows, seat map)`
10. `feat: booking hold with optimistic locking and multi-seat atomicity`
11. `feat: mock payment gateway + booking confirmation`
12. `feat: scheduled hold expiry`
13. `feat: discount codes applied at confirmation`
14. `feat: refund policies + cancellation flow`
15. `feat: async notifications via transactional events`
16. `feat: reminder scheduler`
17. `test: integration tests for core flows`
18. `test: concurrent booking correctness test`
19. `docs: README with assumptions, API examples, and run instructions`

---

## 12. Open questions to resolve before coding

None blocking. Any that surface get logged as `A11`, `A12`, ... in the README.
