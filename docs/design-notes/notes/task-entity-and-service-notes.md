# Task entity and service notes

> **Note**: This document describes the legacy in-memory `ConcurrentHashMap` implementation. The current production system uses JPA persistence with `TaskStore` abstraction. See [ADR-0024](../../adrs/ADR-0024-persistence-implementation.md) for the current architecture.

Related: [Task.java](../../../src/main/java/contactapp/domain/Task.java), [TaskService.java](../../../src/main/java/contactapp/service/TaskService.java), ADR-0010, ADR-0011

## Why Task exists
- Mirrors the Contact model for a simpler domain (id/name/description/status/dueDate) and reuses the shared `Validation` helpers.
- Keeps ID immutable so map keys stay stable; name/description/status/dueDate remain mutable but validated.

## Fields and constraints
- `taskId`: required, trimmed, length 1–10, immutable.
- `name`: required, trimmed, length 1–20, mutable.
- `description`: required, trimmed, length 1–50, mutable.
- `status`: required, TaskStatus enum (TODO, IN_PROGRESS, DONE), defaults to TODO, mutable.
- `dueDate`: optional LocalDate, must not be in the past when set, mutable.
- `projectId`: optional, nullable, associates task with a project; trimmed when set.
- `assigneeId`: optional, nullable, UUID user ID for task assignment (per ADR-0052 Batch 2).
- `createdAt`: required Instant, set automatically on task creation.
- `updatedAt`: required Instant, updated automatically on any modification.
- `archived`: optional boolean, indicates whether the task is hidden from active lists; defaults to false.
- All limits are defined as constants to avoid magic numbers.

## Validation and atomicity
- Constructor and setters call `Validation.validateTrimmedLength` so messages and trimming are consistent.
- Status validated via `Validation.validateNotNull` to ensure non-null enum value.
- Due date validated via `Validation.validateOptionalDateNotPast` when set.
- `update(newName, newDescription, newStatus, newDueDate)` validates all fields first, then assigns together; invalid input leaves state unchanged. Overloaded variants also update projectId/assigneeId associations.
- `updatedAt` timestamp is automatically refreshed on any mutation (update, setProjectId, setAssigneeId).
- `isOverdue()` method returns true if the task has a due date that is before the current date; tasks with no due date are never considered overdue.
- `Task.reconstitute()` factory method allows loading existing tasks from persistence without applying the "not in past" rule for dueDate, since tasks naturally become "overdue" over time.

## Service summary
- `TaskService` is a lazy singleton backed by `ConcurrentHashMap<String, Task>` in the legacy implementation. The current production system uses JPA persistence with `TaskStore` abstraction (see ADR-0024).
- `addTask` rejects null, checks `existsById()` for uniqueness, then calls `save()` (upsert via `put`).
- `deleteTask` trims/validates IDs before removal; `updateTask` uses `findById` then `save` within a transaction for atomic lookup + update, then delegates to `Task.update(name, description, status, dueDate)`.
- `getDatabase()` returns defensive copies of each Task (via `copy()`) in an unmodifiable map; `clearAllTasks()` (package-private) resets state for tests, preventing accidental production use while allowing test access from the same package.
- `copy()` validates source state and uses `reconstitute()` to preserve overdue tasks and existing timestamps, keeping defensive copies aligned with validation rules.

## Clock injection for timezone-safe date validation
- Per ADR-0053, due date validation uses a configurable `Clock` bean instead of hardcoded `Clock.systemUTC()`.
- The "not in past" validation is performed at the **service layer**, not in domain objects, because services have access to the injected Clock.
- `TaskService` receives `Clock` via constructor injection from Spring's DI container.
- The `ClockConfig.java` bean configures the clock using `app.timezone` from application.yml.
- This solves the timezone bug where users behind UTC had "today" rejected as "in the past".
- Domain objects (`Task.java`) handle only structural validation (lengths, nulls); time-sensitive rules live in services.

## Tests hit
- `TaskTest`: constructor trimming, setter/update happy paths, invalid constructor/setter/update cases (null/blank/over-length), status enum validation, atomic update rejection, and `testCopyRejectsNullInternalState` (parameterized test using reflection to corrupt each field and verify validateCopySource throws). Note: past-date validation tests moved to service layer per ADR-0053.
- `TaskServiceTest`: singleton, add/duplicate/null add, delete success/blank/missing, update success/blank/missing/trimmed IDs with status/dueDate, clear-all, defensive copy verification, and timezone-aware past-date validation using injected Clock.
