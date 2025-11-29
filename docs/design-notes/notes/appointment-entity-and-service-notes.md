# Appointment entity and service notes

Related: [Appointment.java](../../src/main/java/contactapp/Appointment.java), [AppointmentService.java](../../src/main/java/contactapp/AppointmentService.java), ADR-0012, ADR-0013

## Why Appointment exists
- Adds the appointment domain (id/date/description) alongside Contact/Task while reusing shared validation patterns.
- Captures date-specific rules (required, not in the past) and keeps IDs/description constrained like other domains.

## Fields and constraints
- `appointmentId`: required, validated not-blank, trimmed, length 1–10, immutable.
- `appointmentDate`: required `java.util.Date`, must not be null or in the past; stored/returned via defensive copies.
- `description`: required, trimmed, length 1–50, mutable.
- Limits are constants to avoid magic numbers.

## Validation and atomicity
- Strings use `Validation.validateNotBlank` + `validateLength`; dates use `Validation.validateDateNotPast`.
- Constructor, setters, and `update(Date, String)` share the same validation path; `update` validates both inputs before assignment so invalid input leaves state unchanged. Setters and update reuse the same helpers to avoid drift.
- Defensive copies on set/get prevent external mutation of stored dates.

## Service summary
- `AppointmentService` is a lazy singleton backed by `ConcurrentHashMap<String, Appointment>`.
- `addAppointment` rejects null, validates IDs (already trimmed by the entity), and uses `putIfAbsent` to enforce unique IDs.
- `updateAppointment` uses `computeIfPresent` to combine lookup/update without a race window.
- `deleteAppointment`/`updateAppointment` trim/validate IDs; updates delegate to `Appointment.update(...)`.
- `getDatabase()` returns an unmodifiable map of defensive copies (via `Appointment.copy()`, which validates the source and reuses the public constructor so defensive copies stay consistent); `clearAllAppointments()` (package-private) resets state for tests, preventing accidental production use while allowing test access from the same package.

## Tests hit
- `AppointmentTest`: trimmed creation with defensive date copy, setter/update happy paths, invalid constructor/setter/update cases (null/blank/over-length strings, null/past dates), atomic rejection on invalid updates, defensive getter copy.
- `AppointmentServiceTest`: singleton, add/duplicate/null add, add-blank-id guard, delete success/blank/missing, update success/blank/missing/trimmed IDs, clear-all, defensive snapshot, copy-null-guard coverage; future dates are computed relative to “now” to keep “not in the past” rules time-stable.
