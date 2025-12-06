# Appointment entity and service notes

> **Note**: This document describes the legacy in-memory `ConcurrentHashMap` implementation. The current production system uses JPA persistence with `AppointmentStore` abstraction. See [ADR-0024](../../adrs/ADR-0024-persistence-implementation.md) for the current architecture.

Related: [Appointment.java](../../../src/main/java/contactapp/domain/Appointment.java), [AppointmentService.java](../../../src/main/java/contactapp/service/AppointmentService.java), ADR-0012, ADR-0013

## Why Appointment exists
- Adds the appointment domain (id/date/description) alongside Contact/Task while reusing shared validation patterns.
- Captures date-specific rules (required, not in the past) and keeps IDs/description constrained like other domains.

## Fields and constraints
- `appointmentId`: required, validated not-blank, trimmed, length 1–10, immutable.
- `appointmentDate`: required `java.util.Date`, must not be null or in the past; stored/returned via defensive copies.
- `description`: required, trimmed, length 1–50, mutable.
- `projectId`: optional, nullable, associates appointment with a project; trimmed when set.
- `taskId`: optional, nullable, associates appointment with a task; trimmed when set.
- `archived`: boolean flag to mark appointments as archived (defaults to false).
- Limits are constants to avoid magic numbers.

## Validation and atomicity
- String IDs/descriptions use `Validation.validateTrimmedLength` (which validates not-blank and trims the input before measuring length); dates use `Validation.validateDateNotPast`. This matches the Contact/Task validation pattern.
- Constructor validates ID via `validateTrimmedLength`, then stores. Mutable fields (date, description) are delegated to setters.
- `update(Date, String)` validates both inputs before assignment so invalid input leaves state unchanged. Overloaded `update(Date, String, String, String)` also updates projectId/taskId associations. Setters and update reuse the same helpers to avoid drift.
- Defensive copies on set/get prevent external mutation of stored dates.
- `isPast()` method returns true if the appointment date is before the current time, useful for UI indicators.
- `Appointment.reconstitute()` factory method allows loading existing appointments from persistence without applying the "not in past" rule, since appointments naturally become "past" over time.

## Service summary
- `AppointmentService` is a lazy singleton backed by `ConcurrentHashMap<String, Appointment>` in the legacy implementation. The current production system uses JPA persistence with `AppointmentStore` abstraction (see ADR-0024).
- `addAppointment` rejects null, validates IDs (already trimmed by the entity), and uses `putIfAbsent` to enforce unique IDs.
- `updateAppointment` uses `computeIfPresent` to combine lookup/update without a race window.
- `deleteAppointment`/`updateAppointment` trim/validate IDs; updates delegate to `Appointment.update(...)`.
- `getDatabase()` returns an unmodifiable map of defensive copies (via `Appointment.copy()`, which validates the source and uses `reconstitute()` to preserve past dates); `clearAllAppointments()` (package-private) resets state for tests, preventing accidental production use while allowing test access from the same package.

## Tests hit
- `AppointmentTest`: trimmed creation with defensive date copy, setter/update happy paths, invalid constructor/setter/update cases (null/blank/over-length strings, null/past dates), atomic rejection on invalid updates, defensive getter copy.
- `AppointmentServiceTest`: singleton, add/duplicate/null add, add-blank-id guard, delete success/blank/missing, update success/blank/missing/trimmed IDs, clear-all, defensive snapshot, copy-null-guard coverage; future dates are computed relative to “now” to keep “not in the past” rules time-stable.
