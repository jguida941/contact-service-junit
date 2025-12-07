# ADR-0013: AppointmentService Singleton and CRUD Tests

> **Note**: This ADR describes the legacy in-memory implementation. See [ADR-0024](ADR-0024-persistence-implementation.md) for the current JPA persistence architecture.

**Status**: Accepted | **Date**: 2025-11-24 | **Owners**: Justin Guida

**Related**: [AppointmentService.java](../../src/main/java/contactapp/service/AppointmentService.java),
[AppointmentServiceTest.java](../../src/test/java/contactapp/service/AppointmentServiceTest.java),
[Appointment.java](../../src/main/java/contactapp/domain/Appointment.java),
[Appointment requirements](../cs320-requirements/appointment-requirements/requirements.md)

## Context
- Appointment storage must enforce unique IDs, reuse appointment validation, and allow add/delete/update.
- A singleton service avoids fragmented state across callers; tests need a reset hook to avoid cross-test leakage.
- API contracts return booleans for duplicate/missing IDs, mirroring existing services.

## Decision
- Expose a process-wide singleton via `AppointmentService.getInstance()` with synchronized initialization.
- Back the service with `ConcurrentHashMap<String, Appointment>` keyed by trimmed IDs; return an unmodifiable
  snapshot of defensive copies from `getDatabase()` (via `Appointment.copy`, which validates the source and
  reuses the public constructor for the clone) to prevent external mutation.
- `addAppointment` rejects null inputs, validates IDs provided by the entity (already trimmed), and refuses to overwrite existing IDs;
  `deleteAppointment` trims/validates IDs; `updateAppointment` trims/validates IDs and delegates field validation
  to `Appointment.update(...)` via `findById` then `save` within a transaction for atomic updates.
- Provide `clearAllAppointments()` so tests can isolate state.
- Validate behavior in `AppointmentServiceTest`:
  - Future dates are generated relative to “now” to keep the “not in the past” rule time-stable.
  - Singleton identity.
  - Add success, duplicate ID failure, null add throws.
  - Delete success, blank ID throws, missing delete returns false.
  - Update success, blank ID throws, missing update returns false, trims ID.
  - Clear-all empties the backing store.

## Consequences
- Shared singleton state requires explicit test resets but keeps API surface consistent with other services; update
  now uses `findById` then `save` within a transaction for atomic updates.
- **Spring Boot Test Considerations (Added 2025-12-02)**: When using `@SpringBootTest`, static singleton instances persist across test executions while Spring reuses the `ApplicationContext`. This caused `DuplicateResourceException` failures until `TestCleanupUtility` was introduced to reset singletons via reflection before clearing data. See ADR-0047.
- Boolean contracts simplify caller logic for missing/duplicate IDs, but rely on tests to prevent accidental overwrites.
- Defensive copies from `getDatabase()` add minor allocation overhead but protect internal state.

## Alternatives considered
- **Per-request service instances** - rejected; would fragment state and complicate synchronization.
- **Plain HashMap with synchronized methods** - workable but less concurrent-friendly than `ConcurrentHashMap`.
- **Throw on missing IDs** - rejected in favor of boolean contracts consistent with existing services/tests.
