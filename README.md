# Movie Ticket Booking System

A Spring Boot service for a movie ticket booking platform: multiple cities, theaters, screens, shows, and seat-level bookings with time-bound holds, discount codes, payment/refunds, and async notifications. Concurrency-safe seat allocation is the marquee correctness concern.

The full design rationale lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). This README is the practical guide: how to run it, how to call it, and what you should know before poking around.

## Table of contents

- [Stack](#stack)
- [Run it](#run-it)
- [Key assumptions](#key-assumptions)
- [End-to-end walkthrough (curl)](#end-to-end-walkthrough-curl)
- [API surface](#api-surface)
- [Concurrency model](#concurrency-model)
- [Testing](#testing)
- [Repository layout](#repository-layout)
- [What's out of scope](#whats-out-of-scope)

---

## Stack

- Java 21, Spring Boot 4.1.1 (Spring Security 7, Jackson 3)
- Spring Data JPA with H2 (file-based in dev, in-memory in tests)
- JWT auth (jjwt 0.12) with BCrypt password hashes
- Lombok for entity boilerplate
- JUnit 5 + AssertJ + Awaitility

## Run it

Requires JDK 21 and nothing else — the Maven wrapper handles the rest.

```bash
./mvnw spring-boot:run
```

App comes up on `http://localhost:8080`. The dev H2 file DB is created at `./data/mtbs*` on first start, so the schema and any data survive across restarts. To reset, stop the app and `rm -rf data/`.

Run the tests:

```bash
./mvnw test
```

The test suite uses an in-memory H2 (`create-drop`) — completely isolated from the dev DB.

### Creating an admin account

Public `POST /api/v1/auth/register` only creates CUSTOMER accounts (design decision — admins are provisioned out-of-band). To create an admin locally:

1. Register a user normally (see below).
2. Promote them via SQL — e.g. through DBeaver connected to `jdbc:h2:file:./data/mtbs;MODE=LEGACY;AUTO_SERVER=TRUE` (user `sa`, empty password):

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
   ```

3. Re-login to get a JWT with the ADMIN role in it.

## Key assumptions

Cross-cutting decisions that shape the API. Full design justifications in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

| # | Assumption |
|---|---|
| A1 | **`ShowSeat` is the per-show concurrency anchor.** Every `Show` materializes one `ShowSeat` per physical seat at creation. All hold/book/cancel logic runs against `ShowSeat`, guarded by `@Version` optimistic locking and a unique `(show_id, seat_id)` constraint. Never book the raw `Seat`. |
| A2 | **Prices are frozen at show creation.** `ShowSeat.priceAtShow` snapshots `basePrice × pricingTier.multiplier × categorySurcharge` so admin edits to tiers or surcharges never affect bookings that are already priced. |
| A3 | **All-or-nothing seat holds.** A `POST /bookings/hold` request either holds every requested seat or none — no partial claims. Losing threads get `409 Conflict`. |
| A4 | **Hold TTL is configurable, default 5 minutes.** `app.booking.hold-ttl-minutes`. A `@Scheduled` sweeper (`app.booking.hold-sweep-interval-ms`, default 30s) releases expired holds and marks their bookings `EXPIRED`. |
| A5 | **Discount codes have global usage limits only** — no per-user redemption tracking (documented limitation). Codes are stored uppercase, validity is a `[from, until)` window, and usage is bumped atomically inside the confirm transaction. |
| A6 | **Refund policy is a list of tiers.** Each tier is `(minHoursBeforeShow, refundPercent)`. The tier with the largest threshold still `≤` hours-until-show at cancellation time applies. No matching tier → zero refund. |
| A7 | **Discount usage is not reversed on cancellation.** Matches industry standard — reversing would race with fresh redemptions of the same code. |
| A8 | **Payments are mocked.** `MockPaymentGateway` fails any card ending `0000` (test hook), otherwise succeeds. Refunds always succeed. |
| A9 | **Notifications are logged, not sent.** `LoggingNotificationSender` writes structured log lines. Delivery is async via `@TransactionalEventListener(AFTER_COMMIT)` on a dedicated `notificationExecutor` pool, so a rolled-back booking never notifies. |
| A10 | **Failed-payment audit rows survive rollback.** Declined charges are recorded via a dedicated `REQUIRES_NEW` transaction (`PaymentAudit`) so the audit trail is durable even though the outer confirmation transaction rolls back. |
| A11 | **Local-only, single JVM.** No cloud, no multi-instance, no distributed lock. Full request-level concurrency between users on this one JVM is in scope. |

## End-to-end walkthrough (curl)

The tour: admin creates a city → theater → screen → seats → movie → tier → show; customer registers, browses, holds, confirms, cancels. Copy-paste each block; the JSON responses give you the ids for the next call.

Assume `TOKEN` will be shell-exported after login.

### 1. Register + login a customer

```bash
curl -sX POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"correcthorse","name":"Alice"}'

curl -sX POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"correcthorse"}'
# → {"userId":"...", "token":"eyJhbGci..." , ...}
export TOKEN=eyJhbGci...
```

### 2. Log in as admin

Register a user with a different email, promote them to `ADMIN` via SQL (see [Creating an admin account](#creating-an-admin-account)), then log in and export `ADMIN_TOKEN`.

### 3. Admin: seed the catalog and one show

```bash
# City
curl -sX POST localhost:8080/api/v1/admin/cities \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Bangalore","state":"KA"}'
# → {"id":"<CITY_ID>", ...}

# Theater
curl -sX POST localhost:8080/api/v1/admin/theaters \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"PVR Forum","address":"Koramangala","cityId":"<CITY_ID>"}'

# Screen
curl -sX POST localhost:8080/api/v1/admin/screens \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Screen 1","theaterId":"<THEATER_ID>"}'

# Seats (bulk)
curl -sX POST 'localhost:8080/api/v1/admin/screens/<SCREEN_ID>/seats:bulk' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"rows":[{"rowLabel":"A","seatCount":5,"category":"STANDARD"},
                {"rowLabel":"B","seatCount":5,"category":"PREMIUM"}]}'

# Movie
curl -sX POST localhost:8080/api/v1/admin/movies \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Inception","durationMinutes":148,"language":"English","rating":"UA","synopsis":"Dreams."}'

# Pricing tier
curl -sX POST localhost:8080/api/v1/admin/pricing-tiers \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"REGULAR","multiplier":"1.000","active":true}'

# Show (48h out)
curl -sX POST localhost:8080/api/v1/admin/shows \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"movieId":"<MOVIE_ID>","screenId":"<SCREEN_ID>","pricingTierId":"<TIER_ID>",
        "startsAt":"2026-09-27T18:00:00Z","endsAt":"2026-09-27T20:30:00Z",
        "basePrice":"200.00"}'
# → {"id":"<SHOW_ID>", ...}   ShowSeats are materialized automatically
```

### 4. Admin (optional): refund policy + discount code

```bash
# Full refund if cancelled 24h+ before show
curl -sX POST localhost:8080/api/v1/admin/refund-policy/tiers \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"minHoursBeforeShow":24,"refundPercent":"100.00"}'

# 10% off, valid for the next 30 days
curl -sX POST localhost:8080/api/v1/admin/discounts \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"SAVE10","type":"PERCENT","value":"10.00",
        "validFrom":"2026-09-25T00:00:00Z","validUntil":"2026-10-25T00:00:00Z",
        "usageLimit":100}'
```

### 5. Customer: browse and book

```bash
# List available shows
curl -s "localhost:8080/api/v1/public/shows?cityId=<CITY_ID>"

# Seat map for the show
curl -s "localhost:8080/api/v1/public/shows/<SHOW_ID>/seats"

# Hold two seats
curl -sX POST localhost:8080/api/v1/bookings/hold \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"showId":"<SHOW_ID>","showSeatIds":["<SEAT1>","<SEAT2>"]}'
# → {"id":"<BOOKING_ID>","status":"PENDING","expiresAt":"...","totalAmount":"400.00", ...}

# Confirm with a discount code
curl -sX POST 'localhost:8080/api/v1/bookings/<BOOKING_ID>/confirm' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"method":"CARD","instrument":"4111111111111111","discountCode":"SAVE10"}'
# → status=CONFIRMED, amountPayable=360.00, payment SUCCESS

# View history
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/bookings/me
```

### 6. Customer: cancel

```bash
curl -sX POST 'localhost:8080/api/v1/bookings/<BOOKING_ID>/cancel' \
  -H "Authorization: Bearer $TOKEN"
# → status=CANCELLED, refund SUCCESS 360.00 (100% tier, since show is 48h away)
```

### Failure modes to try

| Try | Expected |
|---|---|
| Two curl calls holding the same seat | First wins (`200 OK`), second gets `409 Conflict` |
| Card ending `0000` on confirm | `400 Bad Request "Payment declined: insufficient funds"`. Booking stays PENDING; a FAILED payment row is durably recorded |
| Wait 5 min after hold, then confirm | `409 Conflict "Booking hold has expired"` (or the sweeper marks it EXPIRED first) |
| Cancel a show that has already started | `409 Conflict` |

## API surface

Base path: `/api/v1`. Every response goes through a `@RestControllerAdvice` that emits a consistent JSON error body on failures.

### Public

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Register a CUSTOMER |
| `POST` | `/auth/login` | Exchange credentials for a JWT |
| `GET` | `/public/cities` | List all cities |
| `GET` | `/public/cities/{cityId}/theaters` | Theaters in a city |
| `GET` | `/public/theaters/{theaterId}` | Theater detail |
| `GET` | `/public/movies` | List movies with at least one scheduled show |
| `GET` | `/public/movies/{movieId}` | Movie detail |
| `GET` | `/public/shows` | Filter by `cityId`, `movieId`, `theaterId`, `from`, `to` |
| `GET` | `/public/shows/{showId}` | Show detail |
| `GET` | `/public/shows/{showId}/seats` | Seat map with per-seat status + price |

### Customer (`Authorization: Bearer <TOKEN>`, `ROLE_CUSTOMER`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/bookings/hold` | All-or-nothing hold, returns PENDING booking |
| `POST` | `/bookings/{id}/confirm` | Charge (with optional `discountCode`), confirm booking |
| `POST` | `/bookings/{id}/cancel` | Cancel confirmed booking, refund per policy |
| `GET` | `/bookings/{id}` | Detail (own booking, or any if ADMIN) |
| `GET` | `/bookings/me` | Own booking history |

### Admin (`ROLE_ADMIN`)

- `/admin/cities`, `/admin/theaters`, `/admin/screens`, `/admin/screens/{id}/seats:bulk` — catalog CRUD
- `/admin/movies` — movie CRUD
- `/admin/pricing-tiers` — pricing tier CRUD
- `/admin/shows` — show CRUD (materializes ShowSeats on create) + `/{id}/cancel`
- `/admin/discounts` — discount code CRUD
- `/admin/refund-policy/tiers` — refund policy tier CRUD

## Concurrency model

The DB is the source of truth. Two guards:

1. **`@Version` optimistic locking** on `ShowSeat`. Two transactions racing to hold the same seat both read version `N`, both try to write version `N+1`, exactly one succeeds — the other gets `OptimisticLockException`, which `BookingService.hold` catches and translates to `ConflictException` (HTTP 409).
2. **Unique `(show_id, seat_id)` constraint** on `show_seats`, as belt-and-braces. Even if `@Version` had a bug, the DB refuses two rows for the same show+seat.

The same pattern protects `Discount.usedCount` — the last-slot redemption of a discount code cannot go to two bookings.

Sweeper vs. confirm race: the hold-expiry sweeper runs each booking's expiry in a `REQUIRES_NEW` transaction and returns `false` on lost races. Confirm re-validates seat status and `holdBookingId` inline, so a confirm that arrives one microsecond after the sweeper flipped the booking to EXPIRED cleanly returns `409`.

Async notifications run on a dedicated `notificationExecutor` (core 2, max 4, queue 200) via `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`. Rolled-back transactions never notify; slow sinks never block requests.

## Testing

- **Happy-path integration tests** (`BookingHoldConfirmCancelFlowTest`) — full hold → confirm → cancel with real transactions.
- **Sweeper test** (`BookingHoldExpiryTest`) — `@TestPropertySource` overrides `hold-ttl-minutes=0`, Awaitility polls for the EXPIRED transition.
- **Discount test** (`BookingConfirmWithDiscountTest`) — percent + FLAT paths, usage bump, exhaustion behavior.
- **Failure-paths test** (`BookingFailurePathsTest`) — declined payment leaves the booking PENDING with a durable FAILED payment row; overlapping seat request is rejected.
- **Concurrency test** (`ConcurrentHoldContentionTest`) — 12 threads race to hold the same seat set through a `CyclicBarrier`, asserts exactly one commit wins.

Tests run against an in-memory H2 (`create-drop`) fully isolated from the dev DB. Fixtures are seeded per-test via `TestFixtures`, not SQL scripts.

## Repository layout

```
com.mk.movieticketbooking
├── MovieTicketBookingSystemApplication.java
├── auth/            register, login, JWT filter, JwtService
├── booking/         Booking, BookingService (hold/confirm/cancel), HoldSweeper
├── browse/          public catalog + show browsing endpoints
├── catalog/         City, Theater, Screen, Seat + admin CRUD
├── common/          @ControllerAdvice, exceptions
├── config/          Spring Security, category-surcharge config
├── discount/        Discount, DiscountService, admin CRUD
├── movie/           Movie catalog + admin CRUD
├── notification/    async events, listener, reminder scheduler
├── payment/         PaymentGateway iface + MockPaymentGateway + PaymentAudit
├── pricing/         PricingTier + admin CRUD
├── refund/          RefundPolicyTier + refund calculator + admin CRUD
├── show/            Show, ShowSeat, ShowService (materialization)
└── user/            User entity + repo
```

## What's out of scope

Per the [architecture doc](docs/ARCHITECTURE.md):

- **No UI.** REST only.
- **No cloud / containerization / CI/CD.** Runs on a laptop.
- **No microservices / distributed anything.** One JVM, one H2 DB.
- **No OAuth / SSO / MFA.** Just JWT + BCrypt.
- **No real payment gateway integration.** `MockPaymentGateway` is deterministic.
- **No production observability.** Log lines only; no metrics endpoint, no traces.
- **No email/SMS sending.** `LoggingNotificationSender` writes structured log lines.
- **No per-user discount limits.** Global limits only.
- **No refresh tokens, password reset, or email verification.**
- **No CORS / HTTPS termination.** Assumed to run behind a reverse proxy or on `localhost` only.

The H2 console is intentionally enabled at `/h2-console` (URL `jdbc:h2:file:./data/mtbs;MODE=LEGACY`, user `sa`, empty password) for local inspection.
