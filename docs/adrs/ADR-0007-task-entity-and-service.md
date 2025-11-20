# ADR-0007: Task Entity and Service

Status: Draft
Date: 2025-11-19
Owners: Justin Guida
Related: docs/architecture/2025-11-19-task-entity-and-service.md, docs/requirements/task-requirements/requirements.md, PR #123

## Summary
Implements Task (id, name, description) and TaskService with a singleton store and atomic updates.

## Context
- Task requirements mandate immutable IDs plus bounded name/description fields.
- Existing Contact experience provides proven patterns for validation, storage, and testing.
- Stakeholders need a persistent plan (architecture doc) and a tracked decision (ADR) to coordinate work and reviews.

## Decision
- Adopt the architecture described in `docs/architecture/2025-11-19-task-entity-and-service.md`, using a dedicated `Task` entity with constructor-enforced validation and atomic update helper.
- Manage tasks via a singleton `TaskService` backed by `ConcurrentHashMap<String, Task>` with add/delete/update operations mirroring `ContactService`.
- Centralize validation through the shared `Validation` utility without creating task-specific helper classes until requirements demand it.
- Enforce Definition of Done gates (tests, coverage, mutation, static analysis, security scans, documentation) before merging Task changes.

## Consequences
- Reusing familiar patterns should minimize implementation risk and keep code style uniform, but we must ensure Task-specific constraints (max lengths) are well-covered by tests.
- Singleton service plus shared Validation keeps footprint small but requires diligent testing to avoid cross-test contamination; `clearAllTasks()` must be used in tests.
- Additional tooling gates (mutation, CodeQL, Dependency Check) will extend CI time; plan the schedule accordingly.
