# Contact entity design

(Related: [Contact.java](../../src/main/java/contactapp/Contact.java), [ADR-0001](../../adrs/ADR-0001-validation-normalization.md), [ADR-0003](../../adrs/ADR-0003-update-atomicity.md))

File: docs/design-notes/notes/contact-entity-notes.md

## Why Contact exists
- `Contact` is the single source of truth for contact data and its rules.
- Once a `Contact` is created, it should always be in a valid state.
- All validation for individual fields goes through the `Validation` helpers.
- `Contact` is marked `final` to prevent subclassing that could bypass validation invariants (matching `Task` and `Appointment`).

## Fields and constraints
(Exact limits from `Contact.java`.)

- `contactId`
    - Must not be null or blank.
    - Length between 1 and 10 characters.
    - No setter, so the ID never changes after construction.

- `firstName` and `lastName`
    - Must not be null or blank.
    - Length between 1 and 10 characters.

- `phone`
    - Must be exactly 10 digits.
    - No spaces, no symbols, digits only.

- `address`
    - Must not be null or blank.
    - Length between 1 and 30 characters.

All of these limits are defined as `static final` constants so there are no magic numbers inside the methods.

## ID immutability
- `contactId` is set in the constructor and there is no setter.
- This means once a `Contact` is created, its identity does not change.
- Service code relies on this: the map key (`contactId`) and the object field always match.

## Constructor and setters
- The constructor calls `Validation` helpers (`validateLength`, `validateDigits`) to check all fields before storing them.
- Each setter also calls the same helpers, so updates reuse the exact same rules as construction.
- Values are trimmed before being stored, so leading and trailing spaces are removed.
  - **Example:** `new Contact(" 123 ", " Alice ", " Smith ", "1234567890", " 742 Evergreen ")` ends up storing `"123"`, `"Alice"`, `"Smith"`, `"742 Evergreen"`.
- If a setter receives bad data, it throws with the exact same message the constructor would have used.
  - **Example:** `contact.setPhone("12345")` throws `"phone must be exactly 10 digits"` and leaves the old phone number in place.
- Because all validation is in one place and always used, any `Contact` instance that exists is guaranteed to satisfy the constraints.

## Relationship to update(...)
- `Contact.update(...)` is the atomic update helper described in `contact-update-atomic-notes.md`.
- It uses the same field rules described above, but applies them to all mutable fields at once.

## Simple Summary
`Contact` holds the rules for what a valid contact looks like.  
The ID is set once and never changes.  
All fields go through the `Validation` helpers, both in the constructor and in setters, so any `Contact` object is always valid if it exists.  
Length limits and phone rules are defined as constants instead of magic numbers.
