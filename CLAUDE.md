# CLAUDE.md

Working notes for Claude Code contributing to this repository.

## Project

Local-only Spring Boot 4.1.1 + Java 21 movie ticket booking system. See [README.md](README.md) for setup + API, and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design.

## Ground rules

1. **Local-only, single JVM.** No cloud, Docker, or CI. Concurrent users on one JVM are in scope; distributed anything is not.
2. **The operator runs commands themselves.** Do not run `./mvnw`, `git commit`, `git push`, etc. Suggest and wait.
3. **This file is committed.** Required by the problem statement.
4. **Commits: one concern each, imperative lowercase subject.** Match the existing `git log` style. No attribution trailers.
5. **No premature abstraction.** Add the interface when the second implementation appears.

## Stack

Java 21, Spring Boot 4.1.1 (Jackson 3, Spring Security 7), Spring Data JPA, H2 (file in dev, in-memory in tests), Lombok on entities only, JJWT 0.12, JUnit 5 + AssertJ + Awaitility. Build with `./mvnw`.

## Conventions

- Package-by-feature (`auth`, `booking`, `show`, ...). No top-level `dto/` or `service/` package.
- Controllers thin, services `@Transactional(readOnly = true)` at class level with write overrides.
- DTOs are records. Never accept or return entities from controllers.
- All seat writes go through `ShowSeat` (never `Seat`). Keep `@Version` on `ShowSeat` and `Discount`.
- Translate `OptimisticLockException` / `DataIntegrityViolationException` to `ConflictException`.
- Custom exceptions in `common/exception/`; `GlobalExceptionHandler` maps them to JSON.
- Tests use `@IntegrationTest` meta-annotation + `TestFixtures`. Awaitility for async, no `Thread.sleep`.
