# Mapper Pattern Notes

(Related: [ADR-0034](../../adrs/ADR-0034-mapper-pattern.md))

## What problem this solves
- Domain objects (`Contact`) have validation in constructors
- JPA entities need a no-arg constructor for Hibernate
- If we put @Entity on domain objects, Hibernate bypasses our validation when loading from DB
- We'd end up with potentially invalid domain objects

## What the design is
Keep them separate:

```
Contact (domain)          ContactEntity (JPA)         ContactMapper
- Validation in           - @Entity annotation        - Converts between
  constructor             - No-arg constructor          the two
- Business logic          - Just data holders         - Calls domain
- No JPA annotations      - Database columns            constructor
```

## The key insight
When mapper loads from database:
```java
public Contact toDomain(ContactEntity entity) {
    return new Contact(          // <-- Constructor runs!
        entity.getContactId(),   // Validation happens!
        entity.getFirstName(),
        ...
    );
}
```

Even data from the database goes through validation. If someone manually inserted bad data, we catch it immediately.

## Why not just one class?
```java
// This is problematic:
@Entity
public class Contact {
    public Contact(String id, ...) {
        validate(id);  // This runs when YOU create Contact
    }
    protected Contact() {}  // Hibernate uses THIS - skips validation!
}
```

Hibernate creates objects through the no-arg constructor, bypassing validation entirely.

## Reconstitution Pattern (Task and Appointment)

Domain objects with temporal constraints face different challenges when loading from persistence. We use two reconstitution approaches:

### Task: Timestamp Preservation + Temporal Validation Bypass

Task has timestamps (createdAt, updatedAt) and an optional dueDate with "not in past" validation. Both requirements are handled by a static factory method.

Standard constructor (for new tasks):
```java
public Task(String taskId, String name, String description,
            TaskStatus status, LocalDate dueDate) {
    // Sets createdAt = updatedAt = Instant.now()
    // Validates dueDate is not in the past
}
```

Reconstitution factory method (for loading from persistence):
```java
public static Task reconstitute(
        String taskId, String name, String description,
        TaskStatus status, LocalDate dueDate,
        String projectId, UUID assigneeId,  // UUID per ADR-0052 Batch 2
        Instant createdAt, Instant updatedAt) {
    // Accepts timestamps from entity, preserves original values
    // Bypasses "not in past" validation for dueDate (overdue tasks)
}
```

TaskMapper.toDomain() uses reconstitute():
```java
public Task toDomain(TaskEntity entity) {
    return Task.reconstitute(
        entity.getTaskId(),
        entity.getName(),
        entity.getDescription(),
        entity.getStatus(),
        entity.getDueDate(),
        entity.getProjectId(),
        entity.getAssigneeId(),
        entity.getCreatedAt(),    // From entity!
        entity.getUpdatedAt());   // From entity!
}
```

This prevents timestamp drift AND allows loading overdue tasks without validation errors.

### Appointment: Temporal Validation Bypass

Appointment enforces "date must not be in the past" validation in its constructor. This breaks when loading appointments that were valid when created but have naturally aged.

Standard constructor (enforces temporal validation):
```java
public Appointment(String appointmentId, Date appointmentDate, String description) {
    Validation.validateDateNotPast(appointmentDate, "appointmentDate");
    // ... rest of initialization
}
```

Reconstitution factory method (skips temporal validation):
```java
public static Appointment reconstitute(
        String appointmentId,
        Date appointmentDate,
        String description,
        String projectId,
        String taskId,
        boolean archived) {
    // Validates length and nulls, but NOT the past-date rule
    // Uses private constructor that bypasses temporal validation
}
```

AppointmentMapper.toDomain() uses reconstitute():
```java
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

This allows past appointments to be loaded, displayed, archived, and deleted while still preventing users from creating NEW appointments with past dates.

### When to Use Each Approach

| Scenario | Use This |
|----------|----------|
| Creating NEW appointment via API | Constructor (enforces temporal validation) |
| Loading EXISTING appointment from DB | `Appointment.reconstitute()` (skips temporal validation) |
| Creating NEW task via API | Constructor (sets timestamps to now) |
| Loading EXISTING task from DB | `Task.reconstitute()` (preserves original values) |
| Copying appointment with modifications | `copy()` which delegates to `reconstitute()` |

Contact doesn't need reconstitution because it has no temporal constraints or managed timestamps.

## Simple explanation
"Domain stays pure, entity handles database stuff, mapper bridges them. When loading from the database, we use reconstitution: Task preserves timestamps via a multi-parameter constructor, while Appointment uses a static factory method to bypass temporal validation for records that have naturally aged. This keeps NEW records strictly validated while allowing EXISTING past records to be loaded and managed."