# Agent Guide

Quick links and context for automation/assistant workflows implementing this plan.

## Quick Links

| Resource | Path | Purpose |
|----------|------|---------|
| **Requirements** | `docs/REQUIREMENTS.md` | **START HERE** - Master document with scope, phases, checklist, code examples |
| **Roadmap** | `docs/ROADMAP.md` | Quick phase overview |
| **Index** | `docs/INDEX.md` | Full file/folder navigation |
| **ADR Index** | `docs/adrs/README.md` | Stack decisions (ADR-0014–0019) |
| **CI/CD Plan** | `docs/ci-cd/ci_cd_plan.md` | Pipeline phases including security testing |
| **Backlog** | `docs/logs/backlog.md` | Deferred decisions (do not implement yet) |

## Current State

- **Phase 0 complete**: Defensive copies and date validation fix implemented
- Code is an **in-memory Java library** with Contact/Task/Appointment entities and services
- Latest CI: **148 tests passing**, **100% PITest mutation score**, **100% line coverage**, SpotBugs clean
- No Spring Boot runtime, no persistence, no HTTP layer yet
- Domain validation in `Validation.java` is the **source of truth** for all field rules
- `getDatabase()` methods now return defensive copies; safe to surface over APIs

## Implementation Constraints

### Must Follow
1. **Implement phases in order**: 0 → 1 → 2 → 2.5 → 3 → 4 → 5 → 5.5 → 6 → 7
2. **Use code examples from docs/REQUIREMENTS.md** as canonical patterns (Application.java, Controllers, DTOs) and keep REST paths under `/api/v1` (ADR-0016)
3. **Do not deviate from ADR decisions** without creating a new ADR
4. **Preserve existing domain validation** — controllers construct domain objects, don't duplicate rules
5. **Maintain quality gates**: JaCoCo ≥ 80%, SpotBugs clean, Checkstyle passing
6. **All existing tests must pass** after each phase

### Must Not Do
- Do not implement items in `docs/logs/backlog.md` (UI kit, OAuth2/SSO, WAF are deferred)
- Do not change `Validation.java` validation rules (except the Phase 0 boundary fix in `docs/REQUIREMENTS.md`)
- Do not skip phases or implement out of order
- Do not use different frameworks than specified in ADRs (e.g., no Quarkus, no MongoDB)
- Do not remove or weaken existing tests

### Stack Decisions (from ADRs)
| Component | Decision | ADR |
|-----------|----------|-----|
| Backend | Spring Boot 3 | ADR-0014 |
| ORM | Spring Data JPA/Hibernate | ADR-0014 |
| Migrations | Flyway | ADR-0014 |
| Database | Postgres (prod), H2/Testcontainers (test) | ADR-0015 |
| API | REST `/api/v1`, OpenAPI via springdoc | ADR-0016 |
| Frontend | React + Vite + TypeScript, TanStack Query | ADR-0017 |
| Auth | JWT, Spring Security, @PreAuthorize | ADR-0018 |
| Secrets | Env vars (dev), Vault/secret manager (prod) | ADR-0018 |
| Packaging | Docker, GHCR | ADR-0019 |

## Phase-by-Phase Milestones

See `docs/REQUIREMENTS.md` for the full checklist. Definition of done for each phase:

- **Phase 0 ✅ (complete)**: Defensive copies in Task/ContactService, date validation fixed, all tests pass
- **Phase 1**: Spring Boot starts, health endpoint works, existing tests pass
- **Phase 2**: CRUD endpoints for all 3 entities, OpenAPI accessible
- **Phase 2.5**: Schemathesis runs in CI against spec
- **Phase 3**: Data persists in Postgres, Flyway migrations work
- **Phase 4**: React UI can CRUD all entities
- **Phase 5**: JWT auth protects endpoints, security headers applied
- **Phase 5.5**: ZAP runs in CI, auth tests assert 401/403
- **Phase 6**: Docker images build, compose works

## Existing Code to Preserve

These files contain the domain logic — **do not modify validation rules**:
- `src/main/java/contactapp/Validation.java`
- `src/main/java/contactapp/Contact.java`
- `src/main/java/contactapp/Task.java`
- `src/main/java/contactapp/Appointment.java`

These tests must continue passing:
- `src/test/java/contactapp/*Test.java` (all 7 test classes)

## When Complete

After all phases:
1. All checklist items in `docs/REQUIREMENTS.md` are checked
2. Mark `docs/ci-cd/ci_cd_plan.md` phases 8-10 as complete
3. Add summary to `docs/logs/CHANGELOG.md`
