# Task entity and service notes

Related: [Task.java](../../../src/main/java/contactapp/domain/Task.java), [TaskService.java](../../../src/main/java/contactapp/service/TaskService.java), ADR-0010, ADR-0011

## Why Task exists
- Mirrors the Contact model for a simpler domain (id/name/description) and reuses the shared `Validation` helpers.
- Keeps ID immutable so map keys stay stable; name/description remain mutable but validated.

## Fields and constraints
- `taskId`: required, trimmed, length 1–10, immutable.
- `name`: required, trimmed, length 1–20, mutable.
- `description`: required, trimmed, length 1–50, mutable.
- All limits are defined as constants to avoid magic numbers.

## Validation and atomicity
- Constructor and setters call `Validation.validateLength` so messages and trimming are consistent.
- `update(newName, newDescription)` validates both first, then assigns both together; invalid input leaves state unchanged.

## Service summary
- `TaskService` is a lazy singleton backed by `ConcurrentHashMap<String, Task>`.
- `addTask` rejects null and uses `putIfAbsent` for uniqueness.
- `deleteTask` trims/validates IDs before removal; `updateTask` uses `computeIfPresent` for thread-safe atomic lookup + update, then delegates to `Task.update(...)`.
- `getDatabase()` returns defensive copies of each Task (via `copy()`) in an unmodifiable map; `clearAllTasks()` (package-private) resets state for tests, preventing accidental production use while allowing test access from the same package.
- `copy()` validates source state and reuses the public constructor, keeping defensive copies aligned with validation rules.

## Tests hit
- `TaskTest`: constructor trimming, setter/update happy paths, invalid constructor/setter/update cases (null/blank/over-length), atomic update rejection, and `testCopyRejectsNullInternalState` (parameterized test using reflection to corrupt each field and verify validateCopySource throws).
- `TaskServiceTest`: singleton, add/duplicate/null add, delete success/blank/missing, update success/blank/missing/trimmed IDs, clear-all, defensive copy verification.
