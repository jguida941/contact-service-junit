# Validation helper and Contact rules

(Related: [ADR-0001](../../adrs/ADR-0001-validation-normalization.md), [Contact.java](../../src/main/java/contactapp/Contact.java))

File: docs/design-notes/notes/validation-and-contact-notes.md

## What problem this solves
- Inputs were being validated in many places. This means it was hard to keep consistent.
- Using a helper class means all rules are in one place which makes maintenance easier.
- Contact constructor and setters both use the same helpers, so no risk of divergence.
- Tests can assert exact exception messages because they are consistent.
- **Example:** Without the helper, the constructor might trim and use `isBlank` while the setter uses `isEmpty` and forgets to trim.  
  Passing `"   "` would fail the constructor but succeed the setter, leaving the object in a state the constructor would never allow.
- **Example:** If the max name length changes from 10 to 20 and you only update the constructor, new contacts allow 20 chars but `setFirstName(...)` still rejects anything over 10.  
  By keeping the limits in constants and using `Validation.validateLength(...)` everywhere, both constructor and setter automatically stay in sync.

## What the design is
- All checks live in `contactapp.Validation`.
- Methods like `validateNotBlank`, `validateLength`, `validateDigits`, plus `validateDateNotPast` for domains (Appointment) that constrain dates.
- `validateDateNotPast` uses `Clock.systemUTC()` by default, so "now" is evaluated in UTC. Callers in significantly different timezones should construct dates with UTC awareness. A `Clock` parameter overload enables tests to use `Clock.fixed()` for deterministic boundary testing.
- They throw `IllegalArgumentException` with standard message text.

## How Contact uses it
- The `Contact` constructor calls these helpers for `contactId`, `firstName`, `lastName`, `phone`, and `address`, so a `Contact` object is always valid once it exists.
- We always trim values before validation and before saving them into `Contact`.  
  `Validation.validateLength` calls `value.trim()` first, and the setters store the trimmed value.  
  This removes leading and trailing spaces so the stored data is clean and tests can assert the final trimmed value.
- Phone numbers skip trimming because `validateDigits` enforces digits only.  
  Any whitespace or punctuation fails immediately, which keeps the stored value identical to the input digits.
- `Contact.update(...)` takes all four changeable fields at once.  
  It validates every new value using the same helper methods first and only updates the fields if all values are valid.  
  This gives an all-or-nothing update and lets `ContactService.updateContact(...)` call one method instead of chaining setters.

## Summary
- We centralize all rules in a `Validation` class so the rules are not duplicated.
- That makes tests simpler and error messages consistent.
