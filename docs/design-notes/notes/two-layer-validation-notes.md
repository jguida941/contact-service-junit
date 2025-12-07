# Two-Layer Validation Notes

(Related: [ADR-0032](../../adrs/ADR-0032-two-layer-validation.md))

## What problem this solves
- API needs to return proper 400 errors with field names when input is bad
- Domain objects should NEVER be invalid - a Contact must always be valid once created
- If we only validate at API layer, someone could create invalid objects in tests or future code
- If we only validate at domain layer, API errors would be ugly 500s instead of clean 400s

## What the design is
We validate **twice**:

**Layer 1 - DTO (API boundary)**
```java
public record ContactRequest(
    @NotBlank @Size(max = 10) String id,
    @NotBlank @Size(max = 10) String firstName
) {}
```
- Uses Jakarta Bean Validation annotations
- Spring automatically returns 400 with field-specific errors
- Fast feedback before any business logic runs

**Layer 2 - Domain constructor**
```java
public Contact(String id, String firstName, ...) {
    Validation.validateTrimmedLength(id, "Contact ID", 1, 10);
    // ...
}
```
- Domain is the safety net
- Can't construct an invalid Contact - ever
- Catches bugs in tests, migrations, future code paths

## How they stay in sync
DTOs import constants from domain:
```java
@Size(max = Validation.MAX_ID_LENGTH) String id
```
Change the constant once, both layers update.

## Human-Readable Labels (Both Layers)

Both layers now use **human-readable labels** consistently:

**DTO Layer:**
```java
@NotBlank(message = "First Name must not be null or blank")
@Size(max = Contact.MAX_NAME_LENGTH, message = "First Name length must be between 1 and 10")
String firstName
```

**Domain Layer:**
```java
Validation.validateLength(firstName, "First Name", 1, 10);
// Throws: "First Name length must be between 1 and 10"
```

**Result:** Error messages are user-friendly at both layers.

```
DTO Error:       "First Name must not be null or blank"
Domain Error:    "First Name length must be between 1 and 10"
```

## Clock Injection for Temporal Validation

Temporal validators accept an optional `Clock` parameter:
```java
Validation.validateDateNotPast(date, "Appointment Date");         // Uses Clock.systemUTC()
Validation.validateDateNotPast(date, "Appointment Date", clock);  // Uses provided clock
```

This enables deterministic testing without flakiness. See [ADR-0053](../../adrs/ADR-0053-timezone-safe-date-parsing.md).

## Simple explanation
"DTO validation gives good API errors. Domain validation guarantees objects are always valid. Both use human-readable labels. Belt and suspenders."