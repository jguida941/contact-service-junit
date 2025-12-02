# Agent Guide

Quick links and context for automation/assistant workflows implementing this plan.

## Quick Links

| Resource         | Path                       | Purpose                                                                       |
|------------------|----------------------------|-------------------------------------------------------------------------------|
| **Requirements** | `docs/REQUIREMENTS.md`     | **START HERE** - Master document with scope, phases, checklist, code examples |
| **Roadmap**      | `docs/ROADMAP.md`          | Quick phase overview                                                          |
| **Index**        | `docs/INDEX.md`            | Full file/folder navigation                                                   |
| **ADR Index**    | `docs/adrs/README.md`      | Stack decisions (ADR-0001–0042)                                               |
| **CI/CD Plan**   | `docs/ci-cd/ci_cd_plan.md` | Pipeline phases including security testing                                    |
| **Backlog**      | `docs/logs/backlog.md`     | Deferred decisions (do not implement yet)                                     |

## Current State

- **Phase 5 complete**: All `/api/v1/**` endpoints require JWT auth with Spring Security; per-user data isolation enforced via `user_id` foreign keys, ADMIN-only exports now flow through a POST `/api/v1/admin/query` override that requires CSRF tokens + `X-Admin-Override` headers (legacy `?all=true` is in deprecation until **2026-02-01**), Cookie CSRF tokens issued for SPA/browser flows explicitly set `SameSite=Lax` and inherit `server.servlet.session.cookie.secure`, fully sanitized request logging (masked IPs + CR/LF-stripped method/URI/query/user-agent), bucket4j rate limiting on `/api/auth/*` + API paths (per-username + per-IP throttles with temporary lockouts), password strength validation via Bean Validation regex, CSP headers, 30-minute JWT expirations protected by the dedicated `app.auth.cookie.secure` flag, threat model documented, and OWASP ZAP workflows defined.
- Spring Boot 3.4.12 with layered packages (`contactapp.domain`, `contactapp.service`, `contactapp.api`, `contactapp.persistence`)
- Services annotated with `@Service` for Spring DI while retaining `getInstance()` for backward compatibility
- Legacy `getInstance()` access shares the same Spring proxy once the context starts (or an in-memory fallback before boot), so no proxy unwrapping is required and tests focus on shared behavior/state instead of object identity; backlog tracks the follow-up to delete the static entry points once every caller uses DI. All suites that mutate the singleton (service + legacy tests + the bridge test) use JUnit’s `@Isolated` so Surefire doesn’t run them in parallel and collide on shared IDs.
- REST controllers at `/api/v1/contacts`, `/api/v1/tasks`, `/api/v1/appointments`, plus `/api/auth` for login/register/logout backed by DTOs with Bean Validation mapped to domain objects using `Validation.MAX_*` constants
- React SPA gained a `/login` route, `RequireAuth` guard, and profile/token helpers that rely on HttpOnly cookies (no localStorage tokens) with sessionStorage-backed profile caching; logout pathways clear TanStack Query caches and local state.
- Global exception handler (`GlobalExceptionHandler`) maps exceptions to JSON responses (400, 401, 403, 404, 409 including optimistic locking conflicts) and the custom error controller (`CustomErrorController`) plus `JsonErrorReportValve` ensure ALL errors return JSON (including Tomcat-level errors)
- JacksonConfig disables type coercion for strict OpenAPI schema compliance (ADR-0023); Swagger UI/OpenAPI docs still live at `/swagger-ui.html` and `/v3/api-docs`
- Health/info actuator endpoints available; other actuator endpoints locked down; Prometheus/metrics, correlation IDs, and security headers enabled per ADR-0039
- Local dev can persist data via `docker-compose.dev.yml` (Postgres 16) + `dev` Spring profile (`SPRING_DATASOURCE_*` env vars)
- Latest CI run: **642 tests passing** (648 with ITs; unit + slice + MockMvc + Testcontainers + security + config filters), **94% mutation score** (615/656 mutants killed), **96%+ line coverage on stores, 95%+ on mappers**, SpotBugs clean; total tests now **1066** with added controller/filter/composite-key coverage
- Local `mvn verify` skips the Testcontainers suite by default (`skipITs=true`) to avoid a Docker dependency; run `mvn verify -DskipITs=false` with Docker Desktop/Colima to mirror the CI setup.
- All Schemathesis API fuzzing phases pass (Coverage, Fuzzing, Stateful: 30,668 test cases, 0 failures)
- Persistence implemented via Spring Data JPA repositories + Flyway migrations (Postgres dev/prod, H2/Testcontainers tests)
- Mapper and JPA entity tests cover null guards plus the protected constructors so Hibernate proxies and legacy in-memory stores stay mutation-proof.
- Domain validation in `Validation.java` is the **source of truth** for all field rules
- Controllers use service-level lookup methods (`getAllXxx()`, `getXxxById()`) for better encapsulation
- Test fixtures now clear stale SecurityContext entries and recreate the seed user when missing to avoid FK collisions in legacy singleton service tests.

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
| Component             | Decision                                                    | ADR      |
|-----------------------|-------------------------------------------------------------|----------|
| Backend               | Spring Boot 3                                               | ADR-0014 |
| Scaffold              | Spring Boot 3.4.12 + layered packages                       | ADR-0020 |
| ORM                   | Spring Data JPA/Hibernate                                   | ADR-0014 |
| Migrations            | Flyway                                                      | ADR-0014 |
| Database              | Postgres (prod), H2/Testcontainers (test)                   | ADR-0015 |
| API                   | REST `/api/v1`, OpenAPI via springdoc                       | ADR-0016 |
| Frontend              | React + Vite + TypeScript, TanStack Query                   | ADR-0017 |
| Auth                  | JWT, Spring Security, @PreAuthorize                         | ADR-0018 |
| Secrets               | Env vars (dev), Vault/secret manager (prod)                 | ADR-0018 |
| API response coercion | Disable Jackson type coercion for strict OpenAPI compliance | ADR-0023 |
| Packaging             | Docker, GHCR                                                | ADR-0019 |

## Phase-by-Phase Milestones

See `docs/REQUIREMENTS.md` for the full checklist. Definition of done for each phase:

- **Phase 0 ✅ (complete)**: Defensive copies in Task/ContactService, date validation fixed, all tests pass
- **Phase 1 ✅ (complete)**: Spring Boot starts, health/info endpoints work, services are @Service beans, existing tests pass
- **Phase 2 ✅ (complete)**: CRUD endpoints for all 3 entities, OpenAPI accessible, 71 controller tests, Bean Validation on DTOs
- **Phase 2.5 ✅ (complete)**: Schemathesis runs in CI against spec, workflow hardened (pyyaml, jq, JAR validation)
- **Phase 3 ✅ (complete)**: Data persists in Postgres, Flyway migrations work
- **Phase 4 ✅ (complete)**: React UI can CRUD all entities; Vitest (22 tests) + Playwright E2E (5 tests) added
- **Phase 5 ✅ (complete)**: JWT auth protects endpoints, rate limiting/request logging/Prometheus added, SPA login route guards enforced
- **Phase 5.5 (next)**: ZAP scans execute in CI (baseline + API) and auth tests assert 401/403 before sign-off; password strength validation/CSP/threat model updates already landed, but the enforcement/runbooks finish in this phase.
- **Phase 6**: Docker images build, compose works

## Existing Code to Preserve

These files contain the domain logic — **do not modify validation rules**:
- `src/main/java/contactapp/domain/Validation.java`
- `src/main/java/contactapp/domain/Contact.java`
- `src/main/java/contactapp/domain/Task.java`
- `src/main/java/contactapp/domain/Appointment.java`

These tests must continue passing:
- `src/test/java/contactapp/domain/*Test.java` (domain validation tests)
- `src/test/java/contactapp/service/*Test.java` (service CRUD tests)
- `src/test/java/contactapp/*Test.java` (Spring Boot smoke tests + controller tests)

## Documentation Checklist (After Each Change)

**IMPORTANT**: After making ANY code changes, review ALL of these docs. Don't try to guess which ones need updates - just go through the whole list every time.

### Core Docs (review after every change)
| Document            | Path                     | What to Check/Update                                                      |
|---------------------|--------------------------|---------------------------------------------------------------------------|
| **README.md**       | `README.md`              | Folder Highlights table, test counts, scenario coverage lists, file paths |
| **CHANGELOG.md**    | `docs/logs/CHANGELOG.md` | Add entry under `[Unreleased]` section                                    |
| **INDEX.md**        | `docs/INDEX.md`          | Key Files tables, all file path links                                     |
| **REQUIREMENTS.md** | `docs/REQUIREMENTS.md`   | Current State section, phase status, test counts, checklist items         |
| **agents.md**       | `agents.md`              | Current State, test counts, file paths                                    |
| **ROADMAP.md**      | `docs/ROADMAP.md`        | Phase completion status                                                   |

### ADRs & Architecture (review after every change)
| Document | Path | What to Check/Update |
|----------|------|----------------------|
| **ADR index** | `docs/adrs/README.md` | Add new ADR if architectural decision was made |
| **Existing ADRs** | `docs/adrs/ADR-*.md` | Fix any relative links to source files |
| **Architecture docs** | `docs/architecture/*.md` | Update status, fix links, add new docs if needed |

### Design Notes & Requirements (review after every change)
| Document                    | Path                                            | What to Check/Update                              |
|-----------------------------|-------------------------------------------------|---------------------------------------------------|
| **Design notes**            | `docs/design-notes/notes/*.md`                  | Fix links, add new notes if complex feature added |
| **Requirements checklists** | `docs/requirements/*/requirements_checklist.md` | Check off completed requirements                  |

### Verification Commands
```bash
# Find stale links to old package structure
grep -r "contactapp/[A-Z]" docs/ --include="*.md"

# Find references to specific test counts (update if changed)
grep -r "148 tests\|158 tests\|161 tests" docs/ README.md agents.md

# Verify all tests still pass
mvn test -q
```

## When Complete

After all phases:
1. All checklist items in `docs/REQUIREMENTS.md` are checked
2. Mark `docs/ci-cd/ci_cd_plan.md` phases 8-10 as complete
3. Add summary to `docs/logs/CHANGELOG.md`
