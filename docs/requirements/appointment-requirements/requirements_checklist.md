## Appointment Class Requirements Checklist

_Status updated: 2025-11-24_

- [x] Required unique appointment ID string is no longer than 10 characters.
- [x] Appointment ID is non-null, trimmed, and not updatable.
- [x] Appointment has a required Date field.
- [x] Appointment Date is not in the past.
- [x] Appointment Date is non-null; uses `java.util.Date` and rejects any value where
  `date.getTime()` is strictly less than the current time (e.g., `date.getTime() < clock.millis()`), so "now" is allowed and past dates are rejected.
- [x] Description is a required String, no longer than 50 characters, and
  non-null.

## Appointment Service Requirements Checklist

- [x] Service adds appointments with a unique, trimmed appointment ID.
- [x] Service deletes appointments by appointment ID.
- [x] Service updates existing appointments (date and description) atomically after validation.
