# Diagrams — Movie Ticket Booking System

Visual companion to the design docs. Every diagram is Mermaid — renders live in GitHub, VS Code (Markdown preview or a Mermaid extension), and most Markdown tools.

**Contents:**
1. [System context](#1-system-context)
2. [Domain model](#2-domain-model)
3. [Booking state machine](#3-booking-state-machine)
4. [Concurrency race — why `@Version` wins](#4-concurrency-race--why-version-wins)
5. [Confirm request end-to-end](#5-confirm-request-end-to-end)
6. [Test topology](#6-test-topology)
7. [Deployment topology — current vs. production](#7-deployment-topology--current-vs-production)

---

## 1. System context

Who talks to what. Everything on the right of the outer boundary is in-process.

```mermaid
flowchart LR
    subgraph Clients
        C[Customer]
        A[Admin]
    end

    subgraph App["Spring Boot Application (single JVM)"]
        API[REST API layer]
        SVC[Domain services]
        SCHED[Schedulers]
        NOTIF[Async event listeners]
    end

    subgraph Infra["In-JVM infrastructure"]
        DB[(H2 database)]
        PAY[Mock payment gateway]
        LOG[Log-based notification sink]
    end

    C -- "JWT-authenticated REST" --> API
    A -- "JWT-authenticated REST" --> API
    API --> SVC
    SVC --> DB
    SVC --> PAY
    SVC -- "publishes events" --> NOTIF
    NOTIF --> LOG
    SCHED --> DB
    SCHED -- "publishes events" --> NOTIF
```

In production, H2 would become Postgres, the notification sink would become a queue with a real email/SMS provider — but the interfaces stay the same.

---

## 2. Domain model

The five entities that carry the booking flow, plus where the concurrency guard lives.

```mermaid
erDiagram
    CITY ||--o{ THEATER : "has"
    THEATER ||--o{ SCREEN : "has"
    SCREEN ||--o{ SEAT : "has physical layout"
    SCREEN ||--o{ SHOW : "hosts"
    MOVIE ||--o{ SHOW : "screened as"
    PRICING_TIER ||--o{ SHOW : "prices"
    SHOW ||--o{ SHOW_SEAT : "materializes one per Seat"
    SEAT ||--o{ SHOW_SEAT : "per-show instance of"
    SHOW_SEAT }o--|| BOOKING : "held/booked by"
    USER ||--o{ BOOKING : "makes"
    BOOKING ||--o{ PAYMENT : "has"
    DISCOUNT ||--o{ BOOKING : "applied to"

    SHOW_SEAT {
        UUID id
        UUID show_id
        UUID seat_id
        enum status "AVAILABLE|HELD|BOOKED"
        UUID hold_booking_id
        UUID booking_id
        Instant held_until
        BigDecimal price_at_show "frozen"
        Long version "@Version — the guard"
    }
```

Everything above `SHOW_SEAT` is catalog — cities, theaters, screens, physical seats. `SHOW_SEAT` is where bookings actually happen: one row per show per seat, carrying `@Version` and the frozen price.

---

## 3. Booking state machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : POST /bookings/hold\n(seats → HELD)

    PENDING --> CONFIRMED : confirm succeeds\n(payment OK,\nseats → BOOKED)
    PENDING --> EXPIRED : hold TTL elapsed\n(sweeper releases seats\n→ AVAILABLE)
    PENDING --> PENDING : confirm with declined card\n(booking stays PENDING,\nPaymentAudit records FAILED)

    CONFIRMED --> CANCELLED : POST /bookings/{id}/cancel\n(refund per tier,\nseats → AVAILABLE)

    EXPIRED --> [*]
    CANCELLED --> [*]
    CONFIRMED --> [*] : show ends
```

Four states. `PENDING → EXPIRED` is driven by the scheduled sweeper. `PENDING → PENDING` on a declined card leaves the booking untouched but writes a durable FAILED payment row via `PaymentAudit`'s `REQUIRES_NEW` transaction.

---

## 4. Concurrency race — why `@Version` wins

The scenario the whole design exists to prevent: two users, same seat, same instant.

```mermaid
sequenceDiagram
    autonumber
    participant U1 as User 1
    participant U2 as User 2
    participant S1 as BookingService (T1)
    participant S2 as BookingService (T2)
    participant DB as ShowSeat row (v=7)

    U1->>S1: hold(showId, [A1])
    U2->>S2: hold(showId, [A1])
    S1->>DB: SELECT ... (reads v=7, status=AVAILABLE)
    S2->>DB: SELECT ... (reads v=7, status=AVAILABLE)
    S1->>DB: UPDATE ... status=HELD, version=8 WHERE version=7 ✅
    S2->>DB: UPDATE ... status=HELD, version=8 WHERE version=7 ❌ (0 rows)
    DB-->>S2: OptimisticLockException
    S2->>S2: translate → ConflictException
    S1-->>U1: 200 OK { booking: PENDING }
    S2-->>U2: 409 Conflict
```

Both threads read `version = 7`. Only one `UPDATE WHERE version = 7` matches — the other updates zero rows and Hibernate throws. The service catches it and returns 409. No locks held for the duration of the request; losers lose fast.

---

## 5. Confirm request end-to-end

Everything a `POST /bookings/{id}/confirm` touches, in order.

```mermaid
flowchart TD
    Start([POST /bookings/id/confirm]) --> Sec[Spring Security\nJWT filter → ROLE_CUSTOMER]
    Sec --> Ctl[CustomerBookingController]
    Ctl --> Val[Bean Validation\non ConfirmRequest]
    Val --> Svc["BookingService.confirm()\n@Transactional"]

    Svc --> Load[Load Booking + ShowSeats]
    Load --> Chk{Booking PENDING?\nSeats still HELD by this booking?\nHold not expired?}
    Chk -- no --> C409[throw ConflictException → 409]

    Chk -- yes --> Disc{Discount code?}
    Disc -- yes --> DiscSvc["DiscountService.apply()\n@Version bump on Discount"]
    Disc -- no --> Pay
    DiscSvc --> Pay[MockPaymentGateway.charge]

    Pay --> Ok{Payment OK?}
    Ok -- no --> Audit["PaymentAudit.recordIndependently()\nREQUIRES_NEW → commits alone"]
    Audit --> Bad[throw BadRequestException → 400]

    Ok -- yes --> Save[Save Payment SUCCESS\nMark ShowSeats BOOKED\nMark Booking CONFIRMED]
    Save --> Pub[Publish BookingConfirmed event\ninside transaction]
    Pub --> Commit[[Transaction commits]]
    Commit --> Listener["@TransactionalEventListener\nAFTER_COMMIT + @Async\nnotify Executor"]
    Listener --> Log[LoggingNotificationSender]
    Commit --> Resp[200 OK BookingResponse]
```

Two branches worth calling out:

- **`PaymentAudit`** runs in its own `REQUIRES_NEW` transaction, so a FAILED payment row survives the outer rollback.
- **Event listener** runs *after commit* on a different thread, so a rolled-back booking never notifies.

---

## 6. Test topology

The layered test strategy.

```mermaid
flowchart TB
    subgraph Meta["@IntegrationTest meta-annotation"]
        SBT["@SpringBootTest"]
        AP["@ActiveProfiles(test)"]
    end

    subgraph Fixtures["Test fixtures"]
        TF["TestFixtures bean\n(city → theater → screen → seats → movie → tier → show → user)"]
    end

    subgraph Suite["Test suite"]
        T1[BookingHoldConfirmCancelFlowTest\nhappy path]
        T2[BookingHoldExpiryTest\nsweeper + Awaitility]
        T3[BookingConfirmWithDiscountTest\npercent + flat + exhaustion]
        T4[BookingFailurePathsTest\ndeclined card + overlap]
        T5[ConcurrentHoldContentionTest\n12 threads × 1 seat set\nexactly one wins]
    end

    subgraph Runtime["Runtime"]
        H2[(In-memory H2\ncreate-drop)]
    end

    Meta --> T1
    Meta --> T2
    Meta --> T3
    Meta --> T4
    Meta --> T5
    T1 --> TF
    T2 --> TF
    T3 --> TF
    T4 --> TF
    T5 --> TF
    TF --> H2
```

Every test goes through the same `@IntegrationTest` annotation, seeds through the same `TestFixtures` bean, runs against in-memory H2. `ConcurrentHoldContentionTest` is the load-bearing one — twelve threads racing, exactly one wins.

---

## 7. Deployment topology — current vs. production

What's here vs. what a production version would add.

```mermaid
flowchart LR
    subgraph Current["Current — single JVM, local only"]
        APP[App + H2 + schedulers + notifications]
    end

    subgraph Prod["Production would add..."]
        LB[Load balancer]
        A1[App instance 1]
        A2[App instance 2]
        PG[(Postgres primary)]
        PGR[(Postgres replica)]
        MQ[Kafka / RabbitMQ]
        SCHED2[Clustered scheduler\nQuartz or ShedLock]
        MAIL[Real email/SMS provider]
    end

    Current -.evolves to.-> Prod
    LB --> A1
    LB --> A2
    A1 --> PG
    A2 --> PG
    PG --> PGR
    A1 --> MQ
    A2 --> MQ
    MQ --> MAIL
    A1 --> SCHED2
    A2 --> SCHED2
```

Everything on the right is explicitly out of scope for this codebase. The internal seams are already right — `PaymentGateway` is an interface, notifications are events, the scheduler is a bean — so swapping in the production versions wouldn't change the domain code.

---

## Rendering to PNG

Fastest path:

1. Copy the Mermaid block.
2. Open <https://mermaid.live>.
3. Paste, then **Actions → PNG** (transparent background looks best on dark themes).

Offline via CLI:

```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i docs/DIAGRAMS.md -o diagram.png -t dark -b transparent
```
