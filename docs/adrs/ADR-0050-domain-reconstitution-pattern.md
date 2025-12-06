# ADR-0050: Domain Reconstitution Pattern

**Status**: Partially Implemented | **Date**: 2025-12-03 | **Owners**: Justin Guida

**Related**: [ADR-0001 Validation Normalization](ADR-0001-validation-normalization.md), [ADR-0012 Appointment Validation](ADR-0012-appointment-validation-and-tests.md), [Appointment.java](../../src/main/java/contactapp/domain/Appointment.java), [Task.java](../../src/main/java/contactapp/domain/Task.java)

## Context

Domain objects with temporal validation rules face a fundamental problem when loading from persistence: records that were valid when created naturally become invalid over time.

### The Problem

- **Appointment** enforces `appointmentDate must not be in the past` (validated in constructor and setters)
- **Task** enforces `dueDate must not be in the past` (validated in constructor and setters)
- These validations run every time the object is constructed, including when loading from the database
- **Result**: Existing records with past dates fail to load from persistence, breaking read/update/delete operations

### Why This Happens

1. User creates an appointment for tomorrow (valid)
2. Appointment is saved to database
3. Tomorrow becomes today, then yesterday
4. Mapper attempts to load the appointment from database
5. Constructor runs: `Validation.validateDateNotPast(appointmentDate, "appointmentDate")`
6. Throws `IllegalArgumentException: appointmentDate must not be in the past`
7. The appointment becomes permanently inaccessible

### Attempted Solutions

Early attempts included:
- **Skip validation in mappers** - rejected because it violates "domain objects are always valid" principle
- **Special "update mode" flag** - rejected as too complex and error-prone
- **Remove temporal validation** - rejected because past dates should still be blocked for NEW appointments
- **Use different class for persistence** - rejected as it duplicates domain logic

## Decision

Introduce a **reconstitution pattern** using static factory methods that skip temporal validation when loading existing records from persistence.

### Implementation

Add `static reconstitute(...)` factory methods to domain classes with temporal constraints:

```java
// Appointment.java
public static Appointment reconstitute(
        final String appointmentId,
        final Date appointmentDate,
        final String description,
        final String projectId,
        final String taskId,
        final boolean archived) {
    // Validate ID and description lengths, but NOT the past-date rule
    final String validatedId = Validation.validateTrimmedLength(
            appointmentId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);
    Validation.validateNotNull(appointmentDate, "appointmentDate");
    final String validatedDesc = Validation.validateTrimmedLength(
            description, "description", MIN_LENGTH, DESCRIPTION_MAX_LENGTH);

    // Use private constructor that bypasses past-date validation
    return new Appointment(
            validatedId,
            new Date(appointmentDate.getTime()),
            validatedDesc,
            projectId != null ? projectId.trim() : null,
            taskId != null ? taskId.trim() : null,
            archived,
            true); // skipDateValidation = true
}
```

Private constructor with bypass flag:

```java
private Appointment(
        final String appointmentId,
        final Date appointmentDate,
        final String description,
        final String projectId,
        final String taskId,
        final boolean archived,
        final boolean skipDateValidation) {
    this.appointmentId = appointmentId;
    this.appointmentDate = appointmentDate;
    this.description = description;
    this.projectId = projectId;
    this.taskId = taskId;
    this.archived = archived;
    // skipDateValidation is implicit - constructor doesn't call validateDateNotPast
}
```

### Usage Pattern

```java
// AppointmentMapper.java
@Override
public Appointment toDomain(AppointmentEntity entity) {
    return Appointment.reconstitute(
            entity.getAppointmentId(),
            entity.getAppointmentDate(),
            entity.getDescription(),
            entity.getProject() != null ? entity.getProject().getId() : null,
            entity.getTask() != null ? entity.getTask().getId() : null,
            entity.isArchived());
}
```

### Two Code Paths

| Path | Entry Point | Temporal Validation | Use Case |
|------|-------------|---------------------|----------|
| Constructor | `new Appointment(...)` | ENFORCED | Creating new appointments via API |
| Reconstitution | `Appointment.reconstitute(...)` | SKIPPED | Loading existing appointments from database |

## Consequences

### Positive

- **Existing Records Readable**: Past appointments/tasks can be loaded, displayed, archived, and deleted
- **New Records Protected**: API still blocks creation of appointments/tasks with past dates
- **Domain Integrity Preserved**: Objects remain immutable and internally consistent
- **Explicit Intent**: `reconstitute()` name clearly signals "loading from persistence, not creating new"
- **Type Safety**: Compile-time enforcement that mappers use the correct factory method
- **No Magic Flags**: No boolean parameters in public constructors that could be misused

### Negative

- **Two Construction Paths**: Developers must choose between constructor (new records) and `reconstitute()` (existing records)
- **More Code**: Requires private constructor with bypass flag, adds ~30 lines per entity
- **Potential Misuse**: Nothing prevents calling `reconstitute()` from application code (though naming discourages it)
- **Asymmetric Validation**: Constructor validates temporal constraints, `reconstitute()` does not

### Neutral

- **Update Still Blocked**: Calling `update()` on a past appointment with a new past date still throws (intentional)
- **Copy Delegates to Reconstitute**: `copy()` method uses `reconstitute()` to preserve existing state including past dates
- **Test Complexity**: Tests must use `reconstitute()` when creating fixtures with past dates

## Affected Entities

| Entity | Temporal Constraint | Factory Method |
|--------|---------------------|----------------|
| **Appointment** | `appointmentDate` must not be in past | `reconstitute(..., archived)` |
| **Task** | `dueDate` must not be in past (optional field) | Uses multi-param constructor with `createdAt`/`updatedAt` for persistence loading (see Task.java lines 116-143). Formal `reconstitute()` method pending. |

**Note**: Task currently uses a multi-parameter constructor for reconstitution (see line 114-141 in Task.java) which accepts `createdAt` and `updatedAt` parameters. The temporal validation for `dueDate` is handled by `Validation.validateOptionalDateNotPast()` which may need similar reconstitution treatment.

## Migration Notes

- **Mappers must be updated**: Change from `new Appointment(...)` to `Appointment.reconstitute(...)`
- **Tests may break**: Tests creating appointments with past dates must switch to `reconstitute()`
- **Backward Compatible**: Public constructors remain unchanged, existing code continues to work
- **Database schema unchanged**: No migration required, purely code-level pattern

## Why Not Remove Temporal Validation?

Temporal validation serves a legitimate business purpose:
- Prevents scheduling appointments in the past (user error)
- Prevents backdating tasks to manipulate metrics
- Provides immediate feedback at API layer with proper 400 errors

The issue is not the validation rule itself, but applying it to records that **were valid when created** but have naturally aged. Reconstitution solves this by distinguishing "create new" from "load existing".

## Interview Explanation

"Domain objects enforce temporal validation to prevent users from creating appointments or tasks with past dates. But appointments naturally become 'past' over time - an appointment created for tomorrow eventually becomes yesterday. If we run the same validation when loading from the database, those records become inaccessible. The reconstitution pattern solves this by providing a separate factory method that skips temporal validation when loading existing records, while still enforcing it for new records created through the API. It's a two-door approach: strict validation at the front door (API), bypass for the back door (database)."

## References

- Domain-Driven Design concept of "reconstitution" (Evans, 2003)
- Similar pattern used in event sourcing for replaying historical events
- JPA's `@PostLoad` could be an alternative but mixes persistence concerns into domain
