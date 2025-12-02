# ADR-0024: Persistence Implementation Strategy

**Status:** Accepted, Phase 3 | **Date:** 2025-12-01 | **Owners:** Justin Guida

## Context

Phases 0–2.5 relied on singleton services backed by static `ConcurrentHashMap`s. That
approach satisfied early milestones but failed to meet the Phase 3 requirements in
`docs/REQUIREMENTS.md`: real Postgres persistence, Flyway migrations, profile-specific
configuration, and Testcontainers-backed integration tests. Additionally, the domain
classes (`contactapp.domain.*`) are `final` with immutable IDs, so annotating them with JPA
would have broken validation rules and prevented Hibernate proxies. We also have a hard
constraint to retain `ContactService.getInstance()` (and peers) for backward compatibility.

### Why the map design could not continue
- Restarting the JVM wiped all data because the `ConcurrentHashMap` only lived in memory.
- Running multiple instances behind a load balancer resulted in divergent maps with no shared source of truth.
- There was no way to evolve schemas, enforce constraints, or run SQL tooling because no real database existed.
- ADR-0014/0015 already committed us to Postgres + Flyway + Testcontainers, but the services ignored that stack.
- Tests proved only the map behavior and missed all bugs related to persistence or migrations.

## Decision

1. **Layered Persistence Package** – Introduce dedicated JPA entities under
   `contactapp.persistence.entity.*`, mapper components in `contactapp.persistence.mapper.*`,
   and Spring Data repositories in `contactapp.persistence.repository.*`. Domain objects stay
   pure and continue to enforce all validation via constructors/setters.
2. **`DomainDataStore` Abstraction** – Services depend on `ContactStore`, `TaskStore`, and
   `AppointmentStore` interfaces (extending a shared `DomainDataStore`). Spring wires JPA-backed
   implementations while `getInstance()` lazily creates in-memory stores so legacy callers
   continue working.
3. **Flyway + Profiles** – Add Flyway SQL migrations (`db/migration/V1__...` etc.) and
   multi-document `application.yml` that configures Postgres for `dev`/`prod`, H2 for
   unit/slice `test`, and Testcontainers-backed Postgres for `integration`. Hibernate runs
   in `ddl-auto=validate` so schema drifts are caught by migrations.
4. **Testing Strategy** – SpringBootTests/MockMvc/service suites run on the `integration`
   profile with Postgres + Flyway via Testcontainers; repository slices stay on H2 + Flyway
   for speed; mapper unit tests and legacy `getInstance()` tests remain alongside.

## Consequences

- Domain validation remains untouched; `toDomain` always reconstructs aggregates via their
  constructors, so even corrupted DB rows go through the same guard rails.
- Services are no longer `final` so Spring can create transactional proxies. All CRUD methods
  run inside transactions, and read methods use `@Transactional(readOnly = true)`.
- The legacy `getInstance()` API now references an in-memory `DomainDataStore` fallback but
  automatically migrates data into the JPA-backed store once Spring finishes wiring beans.
- Documentation (README, agents.md, REQUIREMENTS, ROADMAP, INDEX, CHANGELOG) now references
  the persistence stack instead of ConcurrentHashMap storage, clarifying how to configure
  profiles and run migrations.

## Concurrency: Hybrid Duplicate Detection

The `addContact()`, `addTask()`, and `addAppointment()` methods use a hybrid approach
combining fast-path rejection with database constraint safety:

```java
// Hybrid approach: fast-path check + database constraint fallback
if (store.existsById(id)) {
    return false;  // Fast rejection for in-memory and JPA stores
}
try {
    store.save(entity);
    return true;
} catch (DataIntegrityViolationException e) {
    // Race condition caught by database constraint
    return false;
}
```

**Why hybrid?**
1. **Fast-path rejection**: The `existsById()` check provides immediate rejection for
   duplicates in both in-memory and JPA stores without waiting for a database round-trip.
2. **Race condition safety**: For JPA stores, if two concurrent requests both pass the
   `existsById()` check, the database UNIQUE constraint catches the race—one insert
   succeeds, the other throws `DataIntegrityViolationException`.
3. **In-memory compatibility**: The in-memory stores (used by legacy `getInstance()`
   callers) don't throw constraint exceptions, but the fast-path check handles duplicates.

Controllers translate the `false` return value to HTTP 409 Conflict, maintaining the same
API contract across both store implementations.
