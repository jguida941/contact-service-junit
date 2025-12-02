# Roadmap

> For full details, checklist, and code examples, see **[REQUIREMENTS.md](REQUIREMENTS.md)**.

## Implementation Order

```
Phase 0   ✅ Pre-API fixes (defensive copies, date validation)
Phase 1   ✅ Spring Boot scaffold (layered packages, actuator, smoke tests)
Phase 2   ✅ REST API + DTOs + OpenAPI (949 tests; PIT mutation 94% / 96%+ store coverage)
Phase 2.5 ✅ API fuzzing in CI (Schemathesis 30,668 tests, ZAP-ready artifacts)
Phase 3   ✅ Persistence (Spring Data JPA, Flyway, Postgres, Testcontainers)
Phase 4   ✅ React UI (Vite + React 19 + Tailwind v4 + shadcn/ui, full CRUD, Maven-integrated)
Phase 5   ✅ Security + Observability
Phase 5.5 ✅ DAST + auth tests
Phase 6   ✅ Docker packaging + CI (Makefile, docker-build job, GHCR push, health checks)
Phase 7   ✅ UX polish (search/pagination/sorting, toasts, empty states, admin dashboard, a11y)
```

Phase 4 note: Full CRUD UI with React Hook Form + Zod validation. `mvn package` builds UI via frontend-maven-plugin and packages into single JAR. Run with `java -jar target/*.jar`. Frontend tests: Vitest + RTL (22 tests) and Playwright E2E (5 tests). See ADR-0025..0028 for design decisions.

Phase 7 note: UX polish complete with search/pagination/sorting (debounced SearchInput, client-side filtering/sorting, URL state sync), toast notifications (Sonner integration), empty states, admin dashboard (RequireAdmin guard, metrics/users/audit views), and comprehensive accessibility (WCAG 2.1 AA with skip links, ARIA landmarks, keyboard nav, screen reader support).

Phase 3 note: legacy `getInstance()` access now reuses the Spring-managed proxies (or the in-memory fallback pre-boot) without proxy unwrapping; behavior tests verify both entry points stay in sync.

Phase 5 focus: End-to-end authentication/authorization, per-tenant data isolation, Docker/compose deployment with health probes, structured logging/metrics, rate limiting, threat modeling, and a documented ZAP baseline scan. See `docs/REQUIREMENTS.md` for the full checklist (JWT login, SPA-only CORS, security headers, bucket4j throttling, Prometheus/Actuator exposure, CI health checks, and runbook updates). The most recent hardening explicitly sets `SameSite=Lax` (plus inherits the secure-cookie flag) on the SPA-visible `XSRF-TOKEN` cookie so ZAP rule 10054 no longer fails.
Test fixtures now reset stale SecurityContext entries and recreate the seed user when missing to prevent FK/unique collisions during singleton-sharing service tests.

**Project/Task Tracker Evolution (Phases 1-5 Complete - 2025-12-01).** ADR-0045 documents the evolution plan. Phases 1-5 are complete and production-ready:
- **Phase 1 (Project Entity)**: Full CRUD operations, status tracking, migration V7
- **Phase 2 (Task Status/Due Date)**: Task workflow management with status/due dates, migration V8
- **Phase 3 (Task-Project Linking)**: Organize tasks within projects, migration V10
- **Phase 4 (Appointment Linking)**: Link appointments to tasks/projects for context, migration V11
- **Phase 5 (Task Assignment)**: Team collaboration with task assignment and access control, migration V12
- **Phase 6 (Contact-Project Linking)**: Deferred to future (stakeholder management via V13 junction table)

Total test count: 1,026, PIT mutation 94%+, store coverage 96%+, mapper coverage 95%+.

**Key Features Now Available**:
- Create and organize projects with status tracking (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED)
- Manage tasks with status (TODO/IN_PROGRESS/DONE), due dates, and project assignment
- Assign tasks to team members with visibility controls (owner/assignee/project-owner/admin)
- Link appointments to tasks and projects for calendar context
- Filter tasks by project, status, assignee, and due dates via query parameters
- All features fully tested with comprehensive coverage across all layers

## CI/CD Security Stages

```
Phase 8  ✅ ZAP in CI
Phase 9  ✅ API fuzzing in CI
Phase 10 → Auth/role tests in CI
Phase 11 ✅ Docker packaging in CI
```

## Quick Links

- **Master Document**: [REQUIREMENTS.md](REQUIREMENTS.md)
- **ADR Index**: [adrs/README.md](adrs/README.md)
- **CI/CD Plan**: [ci-cd/ci_cd_plan.md](ci-cd/ci_cd_plan.md)
