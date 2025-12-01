# Application Requirements

> **Single source of truth** for scope, architecture, stack decisions, phased plan, and progress tracking.
> Consolidates requirements, roadmap, and checklist into one document.

---

## Table of Contents
1. [Goals](#goals)
2. [Current State](#current-state)
3. [Architecture Direction](#architecture-direction)
4. [Stack Decisions (ADRs)](#stack-decisions-adrs)
5. [Phased Work Plan](#phased-work-plan)
6. [Detailed Requirements](#detailed-requirements)
7. [Master Checklist](#master-checklist)
8. [Code Examples](#code-examples)
9. [Related Documents](#related-documents)

---

## Goals

- Turn the contact/task/appointment library into a user-facing application.
- Deliver a web UI for CRUD, search/filter, and validation feedback.
- Keep existing domain rules/tests intact while adding persistence, API, and deployment story.

---

## Current State

- **Phase 3 complete**: Persistence layer (Spring Data JPA + Flyway + Postgres) implemented; legacy singletons still available for backward compatibility.
- Spring Boot 3.4.12 Maven project with layered packages (`domain`, `service`, `api`, `persistence`).
- Domain classes (`Contact`, `Task`, `Appointment`) with validation rules preserved; `appointmentDate` serialized as ISO 8601 with millis + offset (`yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, UTC).
- Services annotated with `@Service` for Spring DI while retaining `getInstance()` fallbacks powered by in-memory stores that migrate into the JPA-backed stores once Spring initializes; `getInstance()` now simply returns the Spring proxy when available so no manual proxy unwrapping is needed, and the backlog tracks their eventual removal once DI-only usage is confirmed.
- REST controllers expose CRUD at `/api/v1/contacts`, `/api/v1/tasks`, `/api/v1/appointments`.
- DTOs with Bean Validation (`@NotBlank`, `@Size`, `@Pattern`, `@FutureOrPresent`) mapped to domain objects.
- Global exception handler (`GlobalExceptionHandler`) maps exceptions to JSON error responses (400, 404, 409).
- Custom error controller (`CustomErrorController`) ensures ALL errors return JSON, including container-level errors.
- Persistence implemented via Spring Data repositories + mapper components; schema managed by Flyway migrations targeting Postgres (dev/prod) and H2/Testcontainers (tests). The default (no profile) run uses in-memory H2 in PostgreSQL compatibility mode so `mvn spring-boot:run` works out of the box; `dev`/`integration`/`prod` profiles point at Postgres.
- Testcontainers-based integration suites cover Contact/Task/Appointment services against real Postgres.
- Mapper/unit suites now include null-guard coverage plus JPA entity accessor tests to keep persistence mutation-safe even when Hibernate instantiates proxies through the protected constructors.
- OpenAPI/Swagger UI available at `/swagger-ui.html` and `/v3/api-docs` (springdoc-openapi).
- Health/info actuator endpoints available; other actuator endpoints locked down.
- Latest CI: 345 tests passing (unit + slice + Testcontainers integration), 99% mutation score (308/311 mutants killed; PIT cannot eliminate the constant `return true` success-path mutants in the `add*` methods), 99% line coverage on mutated classes, SpotBugs clean.
- Controller tests (71 tests): ContactControllerTest (30), TaskControllerTest (21), AppointmentControllerTest (20).
- Exception handler tests (5 tests): GlobalExceptionHandlerTest validates direct handler coverage (including ConstraintViolationException for path variable validation).
- Error controller tests (34 tests): CustomErrorControllerTest (17) + JsonErrorReportValveTest (17) validate container-level error handling.
- Service tests include lookup method coverage: getAllContacts/getContactById, getAllTasks/getTaskById, getAllAppointments/getAppointmentById, plus mapper/repository slices and legacy singleton tests.
- `ui/qa-dashboard` is a sample Vite/React metrics console, not a product UI.
- No authentication, authorization, logging, or observability yet.
- Controllers use service-level lookup methods (`getAllXxx()`, `getXxxById()`) instead of `getDatabase()` for better encapsulation.
- DTO constraints use static imports from `Validation.MAX_*` constants to stay in sync with domain rules.

---

## Architecture Direction

- **Backend**: Spring Boot REST service layered as controller → application/service → domain → persistence; keep domain validation as the single source of truth.
- **Persistence**: JPA/Hibernate with Flyway migrations; Postgres in prod, H2/Testcontainers for tests.
- **API contract**: JSON REST with request/response DTOs, Bean Validation, and OpenAPI/Swagger UI for consumers.
- **Frontend**: React + Vite SPA (TypeScript) in `ui/contact-app` (separate from QA dashboard) with router, form validation, and API client abstraction.
- **Security**: JWT auth, CORS policy for the SPA, input/output sanitization, security headers, and rate limiting in front of the API.
- **Packaging**: Dockerfile + docker-compose for app + DB; CI builds/publishes images and runs integration/E2E suites.

---

## Stack Decisions (ADRs)

| Component | Decision | ADR |
|-----------|----------|-----|
| Backend | Spring Boot 3 | ADR-0014 |
| Scaffold | Spring Boot 3.4.12 + layered packages | ADR-0020 |
| ORM | Spring Data JPA/Hibernate | ADR-0014 |
| Migrations | Flyway | ADR-0014 |
| Database | Postgres (prod), H2/Testcontainers (test) | ADR-0015 |
| API | REST `/api/v1`, OpenAPI via springdoc | ADR-0016 |
| Frontend | React + Vite + TypeScript, TanStack Query | ADR-0017 |
| Auth | JWT, Spring Security, @PreAuthorize | ADR-0018 |
| Secrets | Env vars (dev), Vault/secret manager (prod) | ADR-0018 |
| Packaging | Docker, GHCR | ADR-0019 |

---

## Phased Work Plan

### Implementation Order
```
0 → 1 → 2 → 2.5 → 3 → 4 → 5 → 5.5 → 6 → 7 → (CI: 8 → 9 → 10)
```

### Phase 0: Pre-API Fixes ✅ (Completed)
Fixes implemented:
- Mutable state leakage in `TaskService.getDatabase()` and `ContactService.getDatabase()`:
  - Previously `Map.copyOf()` made the map unmodifiable but contained Task/Contact objects were still mutable.
  - Now returns defensive copies via `copy()` methods (matching `AppointmentService` pattern).
- Date validation edge case in `Validation.validateDateNotPast()`:
  - Previously used `date.before(new Date())` which was millisecond-sensitive.
  - Now accepts equality (date equal to "now" is valid) to avoid boundary flakiness.
- Added `copy()` methods to Task and Contact classes.
- Added tests verifying defensive copy behavior.
- All existing tests pass.

### Phase 1: Backend scaffold ✅ (Completed)
Implementation details:
- Added Spring Boot 3.4.12 parent POM with starter-web, actuator, and validation dependencies.
- Created `Application.java` Spring Boot entrypoint with `@SpringBootApplication`.
- Reorganized packages into layered architecture:
  - `contactapp.domain` - Domain entities (Contact, Task, Appointment, Validation)
  - `contactapp.service` - Services with @Service annotations (ContactService, TaskService, AppointmentService)
  - `contactapp.api` - REST controllers (populated in Phase 2)
- `contactapp.persistence` - Entities, mappers, repositories, and store abstractions backing the services (Phase 3)
- Added `@Service` annotations while preserving `getInstance()` for backward compatibility.
- Created `application.yml` with profile-based configuration (dev/test/prod).
- Locked down actuator endpoints to health/info only per OWASP guidelines.
- Added Spring Boot smoke tests:
  - `ApplicationTest` - Context load verification
  - `ActuatorEndpointsTest` - Endpoint security verification (health/info exposed, others blocked)
  - `ServiceBeanTest` - Bean presence and singleton verification
- All tests pass, 100% mutation score maintained.
- See ADR-0020 for architectural decisions.

### Phase 2: API + DTOs ✅ (Completed)
Implementation details:
- Added REST controllers: `ContactController`, `TaskController`, `AppointmentController` at `/api/v1/{resource}`.
- Created DTOs with Bean Validation:
  - `ContactRequest`/`ContactResponse` with `@NotBlank`, `@Size`, `@Pattern` for phone validation.
  - `TaskRequest`/`TaskResponse` with `@NotBlank`, `@Size` constraints.
  - `AppointmentRequest`/`AppointmentResponse` with `@FutureOrPresent` for date validation.
- DTO constraints use static imports from `Validation.MAX_*` constants to stay in sync with domain rules.
- Added `GlobalExceptionHandler` with `@RestControllerAdvice`:
  - `IllegalArgumentException` → 400 Bad Request
  - `MethodArgumentNotValidException` → 400 Bad Request (Bean Validation)
  - `ResourceNotFoundException` → 404 Not Found
  - `DuplicateResourceException` → 409 Conflict
  - `HttpMessageNotReadableException` → 400 Bad Request (malformed JSON)
- Added springdoc-openapi dependency; Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs`.
- Added 71 controller tests (30 Contact + 21 Task + 20 Appointment) covering:
  - Happy path CRUD operations
  - Bean Validation boundary tests (exact max, one-over-max)
  - 404 Not Found scenarios
  - 409 Conflict (duplicate ID)
  - Date validation (past date rejection, null date)
  - Malformed JSON handling
- All 261 tests pass, 100% mutation score maintained (159/159 killed).
- Added `GlobalExceptionHandlerTest` (4 tests initially, now 5 with `handleConstraintViolation`) for direct exception handler coverage.
- Added service lookup method tests (13 tests) covering `getAllXxx()` and `getXxxById()` methods.

### Phase 2.5: API Security Testing Foundation (Completed)
Implementation details:
- OpenAPI spec already generated via springdoc-openapi at `/v3/api-docs` and `/swagger-ui.html`.
- Added Schemathesis API fuzzing in CI via `.github/workflows/api-fuzzing.yml`:
  - Starts Spring Boot app, waits for health check, runs Schemathesis.
  - Checks: all (5xx errors, schema violations, edge cases).
  - Exports OpenAPI spec to `target/openapi/openapi.json` for ZAP and other tools.
  - Publishes JUnit XML results and fuzzing summary to GitHub Actions.
- ZAP-compatible artifacts prepared: OpenAPI spec exported as CI artifact (`openapi-spec`).
- Added `scripts/api_fuzzing.py` helper for local fuzzing runs.
- Workflow hardened (CodeRabbit review):
  - Explicit `pyyaml` installation for reliable YAML export.
  - Robust `jq`-based health check instead of fragile `grep` JSON parsing.
  - JAR file validation: verifies exactly one JAR in `target/` before startup.

### Phase 3: Persistence ✅ (Completed)
- Added dedicated persistence layer:
  - JPA entities under `contactapp.persistence.entity`.
  - Mapper components (`contactapp.persistence.mapper`) that re-use domain constructors for validation when reading DB rows.
  - Spring Data repositories plus `DomainDataStore` abstractions (`contactapp.persistence.store`) so services can swap between JPA-backed stores and legacy in-memory fallbacks.
- Services now depend on `ContactStore`/`TaskStore`/`AppointmentStore`:
  - Spring wiring injects the JPA-backed beans.
  - Legacy `getInstance()` callers automatically receive in-memory stores until the Spring bean registers, at which point data migrates into the JPA store.
- Added Flyway migrations (`db/migration/V1__create_contacts_table.sql`, `V2__create_tasks_table.sql`, `V3__create_appointments_table.sql`) mirroring constraints defined in `Validation.java`.
- Rebuilt `application.yml` as a multi-document file with dev/test/integration/prod profiles:
  - Dev/prod use Postgres (configurable via env vars).
  - Test profile uses in-memory H2 in PostgreSQL mode with Flyway enabled for schema parity.
  - Integration profile works with Testcontainers via `@ServiceConnection`.
- Expanded test suite:
  - Spring Boot service tests (H2 + Flyway) for each aggregate.
  - Legacy singleton tests ensuring `getInstance()` still works outside Spring.
  - Mapper unit tests and `@DataJpaTest` slices running Flyway migrations.
  - Testcontainers-based Postgres integration tests verifying persistence wiring and DB constraints.

### Phase 4: UI
- Scaffold `ui/contact-app` (React + TypeScript) with routing for Contacts/Tasks/Appointments.
- Build list + filter/sort views; create/edit forms with inline validation; delete confirmations; optimistic updates.
- Add API client wrapper (env-based base URL), loading/error states, and component + E2E smoke tests (Vitest/RTL + Playwright/Cypress).

### Phase 5: Security + Observability
- Add JWT auth with Spring Security, `@PreAuthorize` for role-based method security, and CORS for the SPA.
- Add structured logging, tracing, and metrics (Micrometer/Actuator + Prometheus); propagate correlation IDs.
- Add security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options).
- Add rate limiting or gateway throttles if exposed publicly.
- Document secrets strategy: env vars for dev; vault/secret manager for prod.

### Phase 5.5: DAST + Runtime Security
- Run OWASP ZAP (API/baseline) in CI against the running test instance; fail on high/critical findings.
- Add auth/role integration tests (401/403 assertions) for protected endpoints.

### Phase 6: Packaging + CI
- Add Dockerfiles for backend and UI; compose file for app + Postgres (+ pgAdmin optional).
- Provide Makefile/task runner for local dev; publish images in CI.
- Extend CI to run unit + integration + UI tests, then build/push images while keeping existing quality gates.

### Phase 7: UX polish and backlog
- Add search/pagination/sorting for large datasets; empty states, toasts, and responsive layout.
- Harden accessibility; instrument feature usage and errors for future iterations.

### CI/CD Security Stages (Phases 8-10)
> See `docs/ci-cd/ci_cd_plan.md` for details. These align with implementation phases:
> - CI Phase 8 (ZAP) → Implementation Phase 5.5
> - CI Phase 9 (API fuzzing) → Implementation Phase 2.5
> - CI Phase 10 (auth tests) → Implementation Phase 5.5

---

## Detailed Requirements

### Backend / API
- Provide a Spring Boot runtime with a main entrypoint.
- Expose JSON REST endpoints for Contact, Task, and Appointment:
  - CRUD operations (list, get by id, create, update, delete).
  - Request/response DTOs with Bean Validation; map to domain objects.
  - Consistent JSON error envelope (e.g., `{ "message": "..." }`) for validation failures.
- Include health/info endpoints.

### Persistence
- Use Postgres for dev/prod data; use H2 and/or Postgres Testcontainers for automated tests.
- Model JPA entities aligned to Contact/Task/Appointment and wire services to repositories.
- Manage schema with Flyway migrations; keep environment-specific profiles (dev/test/prod).

### Frontend UI
- Create a React + Vite + TypeScript app under `ui/contact-app`.
- Provide pages for Contact, Task, and Appointment:
  - List with basic filtering/sorting.
  - Create/edit forms with inline validation messages.
  - Delete confirmation flow.
- Show loading and error states; handle API error messages.
- Responsive layout suitable for desktop and mobile widths.
- Use TanStack Query for data fetching and caching (per ADR-0017).

### Security
- Add authentication (JWT per ADR-0018) and protect mutating endpoints.
- Enforce role-based authorization for create/update/delete with `@PreAuthorize`.
- Configure CORS for the SPA origin and apply standard security headers.
- Apply security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options).
- Define secrets management strategy (env vars for dev; vault/secret manager for prod).

### Observability
- Add structured logging with request correlation IDs.
- Expose metrics/tracing via Spring Actuator (Micrometer) for HTTP and database calls.

### Packaging / DevOps
- Provide Dockerfiles for backend and UI; docker-compose for app + Postgres (+ optional pgAdmin).
- Externalize configuration via environment variables; document sample `.env` for local use.
- CI should run unit + integration + UI tests, build artifacts/images, and publish OpenAPI/UI outputs.

### Security Testing
- Add API fuzzing (Schemathesis or RESTler) against the OpenAPI spec; fail CI on 5xx or schema violations.
- Add DAST (OWASP ZAP baseline/API scan) in CI against a running test instance; fail on high/critical findings.
- Add auth/role integration tests asserting 401/403 for protected endpoints and success for allowed roles.
- Keep security scans documented and repeatable in CI.

### Quality / Testing
- Keep existing unit tests for domain/services.
- Add controller tests for REST endpoints and integration tests with Testcontainers.
- Add UI component tests (Vitest/RTL) and at least a smoke E2E flow (Playwright/Cypress).
- Maintain JaCoCo ≥ 80% coverage gate and keep mutation/static analysis gates in the pipeline.
- Spring Boot tests use Mockito's subclass mock-maker (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) to avoid agent attach issues on newer JDKs while still enabling MockMvc/context testing.

---

## Master Checklist

### Phase 0: Pre-API Fixes ✅
- [x] Add `copy()` method to Task class (matching Appointment pattern)
- [x] Add `copy()` method to Contact class (matching Appointment pattern)
- [x] Update `TaskService.getDatabase()` to return defensive copies
- [x] Update `ContactService.getDatabase()` to return defensive copies
- [x] Fix `Validation.validateDateNotPast()` to accept equality (future-or-now valid)
- [x] Add tests verifying defensive copy behavior
- [x] Verify all existing tests still pass

### Phase 1: Backend Scaffold ✅
- [x] Spring Boot entrypoint created
- [x] Services converted to `@Service` beans
- [x] Package structure split: api/service/domain/persistence
- [x] Health/info endpoints exposed
- [x] Dev/test/prod profiles configured

### Phase 2: API + DTOs ✅
- [x] REST controllers for Contact/Task/Appointment (CRUD)
- [x] DTOs with Bean Validation mapped to domain objects
- [x] Global JSON error handler in place
- [x] OpenAPI/Swagger UI published via springdoc-openapi
- [x] Controller tests added (71 tests: 30 Contact + 21 Task + 20 Appointment)

### Phase 2.5: API Security Testing Foundation ✅
- [x] OpenAPI spec generated automatically from controllers
- [x] API fuzzing (Schemathesis or RESTler) running in CI
- [x] ZAP-compatible artifacts prepared
- [x] Workflow hardened (pyyaml, jq health check, JAR validation)

### Phase 3: Persistence ✅
- [x] Postgres configuration for dev/prod (multi-document `application.yml` + env vars).
- [x] H2 (test profile) and Testcontainers (integration profile) wired for automated tests.
- [x] JPA entities, repositories, and mapper components created for Contact/Task/Appointment.
- [x] Flyway migrations (`V1__contacts`, `V2__tasks`, `V3__appointments`) authored and applied in all environments.
- [x] Profile documentation updated across README/agents/REQUIREMENTS.
- [x] Integration tests with Testcontainers covering all three services + DB constraints.
- [x] Local Postgres persistence wired via `docker-compose.dev.yml` + `dev` Spring profile (see README for env exports).

### Phase 4: Frontend UI ✅
- [x] React + Vite + TypeScript app scaffolded under `ui/contact-app`
- [x] TanStack Query wired for data fetching
- [x] List + filter/sort views for Contact/Task/Appointment
- [x] Create/edit forms with inline validation (React Hook Form + Zod)
- [x] Delete confirmation flow
- [x] Loading and error states handled
- [x] Responsive layout verified
- [x] UI component tests (Vitest/RTL) — 22 tests covering schemas, forms, pages
- [x] UI E2E smoke test (Playwright) — 5 tests covering CRUD happy path

### Phase 5: Security + Observability
- [ ] Authentication (JWT per ADR-0018) implemented with login endpoint + SPA integration
- [ ] Role-based authorization on mutating endpoints (@PreAuthorize with ADMIN vs USER)
- [ ] Per-user/tenant data isolation (add `user_id`/`account_id` to aggregates; repositories/services filter automatically)
- [ ] CORS configured exclusively for the SPA origin; document threat model in `docs/architecture/threat-model.md`
- [ ] Security headers applied (CSP, HSTS, X-Content-Type-Options, Referrer-Policy, X-Frame-Options)
- [ ] Rate limiting configured (bucket4j or similar) for high-risk API paths
- [ ] Structured logging with correlation IDs + PII masking for phone/address
- [ ] Actuator metrics + Prometheus scrape endpoint enabled; basic readiness/liveness probes
- [ ] Dockerfile + docker-compose stack (Spring Boot + Postgres + optional pgAdmin) with CI health check (`curl http://localhost:8080/actuator/health`)
- [ ] ADR updates for authentication/authorization and observability strategy
- [ ] ZAP baseline/API scan run (document findings + fixes or suppressions)
- [ ] README + runbooks updated with deployment, security, and operational notes
- [ ] Secrets management strategy documented (env vars locally, vault/secret manager in prod)

### Phase 5.5: DAST + Runtime Security
- [ ] DAST (OWASP ZAP baseline/API scan) running in CI
- [ ] Auth/role integration tests asserting 401/403 and allowed roles
- [ ] Security scan documentation/checks repeatable in CI
- [ ] Dependency-Check/CodeQL suppressions tracked with reasoning; coverage/mutation thresholds enforced in CI
- [ ] GitHub Actions updated to run `npm run test:run` (Vitest) and `npm run test:e2e` (Playwright) with branch protection on all required jobs

### Phase 6: Packaging + CI
- [ ] Dockerfiles for backend and UI
- [ ] docker-compose for app + Postgres (+ optional pgAdmin)
- [ ] Makefile/task runner for local dev
- [ ] Environment variables documented (sample `.env`)
- [ ] CI builds/tests images and publishes artifacts
- [ ] Health check step in CI to curl `/actuator/health` after startup to ensure container wiring works
- [ ] GH Actions job builds Docker image/compose stack and verifies `/actuator/health` before publishing

### Phase 7: UX Polish + Backlog
- [ ] Search/pagination/sorting for large datasets
- [ ] Empty states and toast notifications
- [ ] Accessibility hardening
- [ ] Feature usage instrumentation

### CI/CD Security Stages
- [ ] Phase 8: ZAP baseline/API scan in CI (fail on high/critical)
- [ ] Phase 9: API fuzzing in CI (fail on 5xx/schema violations)
- [ ] Phase 10: Auth/role integration tests in CI

### Quality Gates (Maintain Throughout)
- [ ] JaCoCo ≥ 80% maintained
- [ ] Mutation/static analysis gates kept in pipeline
- [ ] Controller/DTO patterns aligned across Contact/Task/Appointment

---

## Code Examples

### Spring Boot Entrypoint (Phase 1)
```java
// src/main/java/contactapp/Application.java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### REST Controller + DTOs (Phase 2)
```java
// src/main/java/contactapp/api/ContactController.java
@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {
    private final ContactService service;
    public ContactController(ContactService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@RequestBody @Valid ContactRequest req) {
        var contact = new Contact(req.id(), req.firstName(), req.lastName(), req.phone(), req.address());
        service.addContact(contact);
        return ContactResponse.from(contact);
    }
}

// DTOs (records keep it terse)
public record ContactRequest(String id, String firstName, String lastName, String phone, String address) {}
public record ContactResponse(String id, String firstName, String lastName, String phone, String address) {
    static ContactResponse from(Contact c) {
        return new ContactResponse(c.getContactId(), c.getFirstName(), c.getLastName(), c.getPhone(), c.getAddress());
    }
}

// Global error handler
@ControllerAdvice
class GlobalErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
```

### API Client + TanStack Query (Phase 4)
```ts
// ui/contact-app/src/lib/api.ts — low-level fetch helpers
const base = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

export async function fetchContacts(): Promise<ContactResponse[]> {
  const res = await fetch(`${base}/contacts`);
  if (!res.ok) throw new Error((await res.json()).message ?? "Request failed");
  return res.json();
}

export async function createContact(payload: ContactRequest): Promise<ContactResponse> {
  const res = await fetch(`${base}/contacts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error((await res.json()).message ?? "Request failed");
  return res.json();
}
```

```ts
// ui/contact-app/src/hooks/useContacts.ts — TanStack Query wrappers (per ADR-0017)
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchContacts, createContact } from "../api";

export function useContacts() {
  return useQuery({
    queryKey: ["contacts"],
    queryFn: fetchContacts,
  });
}

export function useCreateContact() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createContact,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
    },
  });
}
```

---

## Related Documents

| Document | Path | Purpose |
|----------|------|---------|
| ADR Index | `docs/adrs/README.md` | Architecture decisions (ADR-0001–0022) |
| CI/CD Plan | `docs/ci-cd/ci_cd_plan.md` | Pipeline phases including security testing |
| Backlog | `docs/logs/backlog.md` | Deferred decisions (UI kit, OAuth2/SSO, WAF) |
| Agent Guide | `agents.md` | AI assistant entry point |
| Repository Index | `docs/INDEX.md` | Full file/folder navigation |
| Original Requirements | `docs/requirements/` | Contact/Task/Appointment milestone specs |

---

## Completion Criteria

When all phases are complete:
1. All checklist items above are checked
2. Mark `docs/ci-cd/ci_cd_plan.md` phases 8-10 as complete
3. Add summary to `docs/logs/CHANGELOG.md`
