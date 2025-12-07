# Task Entity and Service
Status: Implemented
Owner: Justin Guida
Implemented: 2025-11-20
Links: requirements/task-requirements/requirements.md
Summary: Implements Task (id,name,description,status,dueDate) and TaskService with singleton store and atomic updates.

## Implementation Plan Overview

## Implementation Summary

Implemented in:
- `src/main/java/contactapp/domain/Task.java`
- `src/main/java/contactapp/service/TaskService.java`
- `src/test/java/contactapp/domain/TaskTest.java`
- `src/test/java/contactapp/service/TaskServiceTest.java`

Key points:
- Task fields and limits mirror the requirements (id 1–10 chars, name 1–20, description 1–50, status enum, optional dueDate) and reuse the shared `Validation` helpers including `validateNotNull` for enum and `validateOptionalDateNotPast` for dueDate.
- `Task.update(...)` validates both mutable fields first so updates remain atomic and exception-safe.
- `TaskService` is a lazy singleton backed by `ConcurrentHashMap`, with `add`/`delete`/`update` returning booleans; `addTask` relies on the Task constructor having already trimmed the ID, while `deleteTask` and `updateTask` explicitly trim their ID parameters; `updateTask` uses `findById` then `save` within a transaction for atomic lookup + update.
- `getDatabase()` returns defensive copies (via `Task.copy()`) in an unmodifiable map, preventing external mutation of internal state.
- Tests cover constructor trimming, negative validation cases, singleton behavior, duplicate handling, defensive copy verification, and the `clearAllTasks()` reset hook (needed for PIT).

## Definition of Done
- All unit tests pass via `mvn verify`.
- Line coverage: current JaCoCo reports 100% on mutated classes.
- Mutation score: PITest shows 100% mutation score (all mutants killed).
- Checkstyle and SpotBugs: zero new issues for Task-related files.
- CodeQL and Dependency Check: no Task-specific findings as of 2025-11-20.
- Public types/methods include Javadoc on Task and TaskService.
- README, changelog, requirements checklist, and architecture/ADR references updated to include the Task feature.

## Phase Breakdown

### Phase 1 - Domain & Validation Foundations
- Confirm requirements from `docs/requirements/task-requirements/requirements.md` and align with Contact patterns.
- Implement `Task` class:
  - Fields: `taskId` (immutable, ≤10 chars), `name` (mutable, ≤20), `description` (mutable, ≤50), `status` (TaskStatus enum, defaults to TODO), `dueDate` (optional LocalDate, must not be past).
  - Constructor validates and trims inputs; delegate mutable fields to setters.
  - Provide getters, `setName`, `setDescription`, `setStatus`, `setDueDate`, and `update(String, String, TaskStatus, LocalDate)` for atomic updates.
- Reuse `Validation.validateLength` / `validateNotBlank`; extend Validation only if new shared helpers become necessary.

### Phase 2 - Service Layer Implementation
- Design `TaskService` mirroring `ContactService`:
  - Singleton with lazy-loaded `getInstance`.
  - Backing store `ConcurrentHashMap<String, Task>` for add/update/delete.
  - `addTask(Task)` enforces unique IDs via `existsById()` check, then `save()` (upsert via `put`).
  - `deleteTask(String taskId)` validates input then removes entry.
  - `updateTask(String taskId, String name, String description)` delegates to the Task instance and returns success boolean.
  - Provide `getDatabase()` (defensive copies via `Task.copy()`) and `clearAllTasks()` to aid testing and isolation.
- Ensure all public methods carry Javadoc consistent with Definition of Done.

### Phase 3 - Testing & Quality Gates
- Expand `TaskTest` with:
  - Happy-path construction, trimming behavior, setter updates, bulk update.
  - Negative cases for every constraint (null, blank, over-length) mirroring Contact tests.
  - Parameterized invalid update cases that prove `update(...)` is atomic and leaves prior state intact on validation failure.
- Build `TaskServiceTest` to verify add/delete/update flows, uniqueness enforcement, and exception propagation; use `clearAllTasks()` in lifecycle hooks.
- Run unit tests plus coverage/mutation tooling (e.g., JaCoCo, PIT) to meet thresholds; document results.
- Execute Checkstyle, SpotBugs, CodeQL, and Dependency Check; resolve or document findings.
- Update relevant docs (requirements checklist, README, CHANGELOG) to reflect Task feature completion.

## Deviations from Plan
- No deviations; Validation helpers were reused as expected and quality gates met the stated thresholds.
