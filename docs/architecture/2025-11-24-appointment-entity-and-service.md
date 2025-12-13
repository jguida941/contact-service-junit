# Appointment Entity and Service
Status: Implemented  
Owner: Justin Guida  
Implemented: 2025-11-24  
Links: [cs320-requirements/appointment-requirements/requirements.md](../cs320-requirements/appointment-requirements/requirements.md), [ADR-0012](../adrs/ADR-0012-appointment-validation-and-tests.md), [ADR-0013](../adrs/ADR-0013-appointmentservice-singleton-and-crud-tests.md)<br>
Summary: Implements Appointment (id, date, description) with date-not-past validation and an AppointmentService singleton for add/delete/update.

## Implementation Plan Overview
Mirror the Contact/Task patterns: immutable trimmed ID (≤10), required description (≤50), required `java.util.Date` not in the past, defensive date copies; singleton service backed by `ConcurrentHashMap<String, Appointment>` with boolean add/delete/update.

## Implementation Summary

Implemented in:
- `src/main/java/contactapp/domain/Appointment.java`
- `src/main/java/contactapp/service/AppointmentService.java`
- `src/test/java/contactapp/domain/AppointmentTest.java`
- `src/test/java/contactapp/service/AppointmentServiceTest.java`

Key points:
- Appointment fields enforce the requirements: trimmed, immutable `appointmentId` (1–10 chars); description required, ≤50 chars; `appointmentDate` required, not in the past, validated via `Validation.validateNotBlank` + `validateLength` + `validateDateNotPast`.
- Dates are stored/returned as defensive copies to prevent external mutation.
- `Appointment.update(...)` validates both inputs before assigning so invalid inputs leave state unchanged; defensive copies live in one place.
- `AppointmentService` is a lazy singleton backed by `ConcurrentHashMap`, using `existsById()` for uniqueness checks and `save()` (upsert via `put`) for persistence, trimming IDs on add/delete/update, and delegating field rules to `Appointment.update(...)`. `getDatabase()` returns a copy built by `Appointment.copy()` (which reuses the public constructor after validating the source); `clearAllAppointments()` resets state for tests.
- Tests cover singleton reuse, add/duplicate/null add, delete success/blank/missing, update success/blank/missing/trimmed IDs, clear-all, and domain validation (null/past dates, description limits, defensive date copies). Test dates are relative (no stale hard-coded years).

## Definition of Done
- All unit tests pass via `mvn verify`.
- Line coverage: current JaCoCo reports 100% on mutated classes.
- Mutation score: PITest shows all mutants killed on Appointment/AppointmentService as of 2025-11-24.
- Checkstyle and SpotBugs: zero new issues for Appointment-related files.
- CodeQL and Dependency Check: no Appointment-specific findings as of 2025-11-24.
- Public types/methods include Javadoc on Appointment and AppointmentService.
- README, changelog, requirements checklist, architecture/ADR references updated to include the Appointment feature.

## Phase Breakdown

### Phase 1 - Domain & Validation Foundations
- Confirm requirements from `docs/cs320-requirements/appointment-requirements/requirements.md` and align with Contact/Task patterns.
- Implement `Appointment` class:
  - Fields: `appointmentId` (immutable, ≤10 chars), `appointmentDate` (required `Date`, not in past), `description` (required, ≤50 chars).
  - Constructor validates and trims string inputs; delegates mutable fields to setters; stores dates via defensive copies.
  - Provide getters, `setDescription`, and `update(Date newDate, String newDescription)` for atomic updates.
- Add `Validation.validateDateNotPast` to centralize date checks.

### Phase 2 - Service Layer Implementation
- Design `AppointmentService` mirroring existing services:
  - Singleton with lazy-loaded `getInstance`.
  - Backing store `ConcurrentHashMap<String, Appointment>` for add/update/delete.
  - `addAppointment(Appointment)` enforces unique IDs via `existsById()` check then `save()` (upsert via `put`), rejects null inputs, and validates IDs (already trimmed by the entity) so stored keys match object IDs.
  - `deleteAppointment(String appointmentId)` validates/trims input then removes entry.
  - `updateAppointment(String appointmentId, Date date, String description)` trims/validates ID and uses `findById` then `save` within a transaction for atomic updates; returns success boolean.
  - Provide `getDatabase()` snapshot (defensive copies via `Appointment.copy`) and `clearAllAppointments()` for test isolation; add Javadoc consistent with Definition of Done.

### Phase 3 - Testing & Quality Gates
- Expand `AppointmentTest` with:
  - Happy-path construction (trim, defensive date copy), setter/update success.
  - Negative cases for every constraint: null/blank/over-length ID/description, null/past date.
  - Atomicity tests showing invalid updates leave state unchanged; defensive copy assertions on getters.
- Build `AppointmentServiceTest` to verify add/delete/update flows, uniqueness enforcement, ID trimming, and exception/boolean contracts; use `clearAllAppointments()` in lifecycle hooks; generate future dates relative to “now” to keep past-date validation time-stable.
- Run unit tests plus coverage/mutation tooling (JaCoCo, PIT) to meet thresholds; document results.
- Execute Checkstyle, SpotBugs, CodeQL, and Dependency Check; resolve or document findings.
- Update relevant docs (requirements checklist, README, CHANGELOG) to reflect Appointment feature completion.

## Deviations from Plan
- None; validation helper was extended for dates, and quality gates/documentation were aligned with existing domain/service patterns.
