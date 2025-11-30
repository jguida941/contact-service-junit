# Changelog
All notable changes to this project will be documented here. Follow the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) spirit and use
[Semantic Versioning](https://semver.org/) once formal releases begin.

## [Unreleased]
### Security
- **react-router vulnerability**: Bumped `react-router` and `react-router-dom` from 7.0.2 to 7.9.6 to fix CVE for pre-render data spoofing.

### Fixed
- **SpotBugs false positive**: Added `@SuppressFBWarnings` to `ApplicationContextProvider.setApplicationContext()` for standard Spring ApplicationContextAware pattern.
- **Race condition in add methods**: Fixed check-then-insert race condition in `ContactService.addContact()`, `TaskService.addTask()`, and `AppointmentService.addAppointment()` using a hybrid approach: fast-path `existsById()` check for immediate rejection plus `DataIntegrityViolationException` catch for race condition safety in JPA stores. This provides optimal performance (no DB round-trip for obvious duplicates) while ensuring atomicity via database UNIQUE constraints. See ADR-0024 for details.
- **UI API endpoint mismatch**: Changed API base from `/api` to `/api/v1` to match backend controller mappings.
- **Appointment date field name**: Fixed `date` → `appointmentDate` to match backend `AppointmentRequest` DTO.
- **Missing ID fields in forms**: Added ID input field to Contact, Task, and Appointment create forms (backend requires ID in request body).
- **Vite proxy configuration**: Updated proxy to forward `/api/v1`, `/actuator`, `/swagger-ui`, and `/v3/api-docs` to backend.

### Added
- **Settings page** (`SettingsPage.tsx`): Profile management (name, email with localStorage persistence), appearance settings (dark mode toggle, 5 color themes), and data management (clear settings).
- **Help page** (`HelpPage.tsx`): Getting started guide, feature documentation, developer resources (Swagger UI, OpenAPI spec, health check links), keyboard shortcuts.
- **Profile hook** (`useProfile.ts`): Manages user profile (name, email, initials) in localStorage with auto-computed initials.
- **TopBar navigation**: Avatar dropdown now links to Settings and Help pages, shows user initials from profile.
- **README Development URLs table**: Clear distinction between `:5173` (React UI) and `:8080` (API/Swagger).

- **Phase 4 React UI (complete)**:
  - Scaffolded `ui/contact-app/` with Vite + React 19 + TypeScript + Tailwind CSS v4.
  - Installed shadcn/ui components (Button, Card, Table, Sheet, Dialog, Input, Label, etc.) with Radix UI primitives.
  - Created app shell layout: `AppShell.tsx` (main layout), `Sidebar.tsx` (collapsible navigation), `TopBar.tsx` (title, theme switcher, dark mode toggle).
  - Implemented 5 professional themes (Slate, Ocean, Forest, Violet, Zinc) with light/dark variants via CSS variables.
  - Built `useTheme` and `useMediaQuery` hooks for theme switching and responsive breakpoints.
  - Created `lib/api.ts` (typed fetch wrapper with error normalization), `lib/schemas.ts` (Zod schemas matching `Validation.java` constants), `lib/utils.ts` (`cn()` class merger).
  - Built full CRUD pages: `OverviewPage` (dashboard), `ContactsPage`, `TasksPage`, `AppointmentsPage` with create/edit forms and delete confirmation dialogs.
  - Created form components (`ContactForm`, `TaskForm`, `AppointmentForm`) using React Hook Form + Zod validation mirroring backend constraints.
  - Added `DeleteConfirmDialog` component for delete confirmations.
  - Set up React Router v7 with nested routes and TanStack Query for server state (queries + mutations).
  - Configured Vite dev server proxy (`/api` → `localhost:8080`).
  - Added `frontend-maven-plugin` and `maven-resources-plugin` to `pom.xml` for integrated build.
  - `mvn package` now builds UI and packages into single executable JAR with static assets at `/`.
  - Authored ADR-0025 (UI Component Library), ADR-0026 (Theme System), ADR-0027 (App Shell Layout), ADR-0028 (Build Integration).
  - Updated README with React UI Layer section, Getting Started commands for dev/prod modes.
  - Updated INDEX.md with UI files section, ROADMAP.md marking Phase 4 complete.
- Mapper null-guard tests, fresh JPA entity accessor suites, and additional `findById` Optional-empty assertions in the legacy `InMemory*Store` tests so persistence and fallback layers cover both branches, pushing the suite to **345 tests** with **100% mutation kills** and **99% line coverage on mutated classes**.
- Legacy singleton regression coverage ensuring `registerInstance` migrates in-memory data into the Spring-managed services (Contact/Task/Appointment).
- Unit tests for `InMemoryContactStore`, `InMemoryTaskStore`, and `InMemoryAppointmentStore` that prove defensive copies and delete semantics so PIT can mutate those branches safely.
- Added fallback tests calling `ContactService.getInstance()` (and Task/Appointment variants) with both Spring context and legacy cold-start scenarios so PIT's null-return mutants are exercised, resulting in **100% mutation score (307/307)** with 93% line coverage on mutated classes.
- Added `ServiceSingletonBridgeTest` to prove `getInstance()` delegates to the Spring `ApplicationContext`, plus configuration tests (`JacksonConfigTest`, `TomcatConfigTest`) so PIT exercises the strict JSON wiring and valve registration.
- **Phase 3 Persistence Stack**:
  - Added Spring Data JPA, Flyway (core + Postgres), PostgreSQL JDBC driver, H2, and Testcontainers dependencies to `pom.xml`.
  - Introduced `contactapp.persistence.entity|mapper|repository|store` packages. Services now depend on `ContactStore`/`TaskStore`/`AppointmentStore`, which delegate to Spring Data JPA under normal operation and in-memory stores for legacy `getInstance()` callers.
  - Created Flyway migrations `V1__create_contacts_table.sql`, `V2__create_tasks_table.sql`, and `V3__create_appointments_table.sql` (constraints derived from `Validation.java`).
  - Rebuilt `application.yml` as a multi-document profile file (dev/test/integration/prod) with Flyway/JPA configuration per profile.
  - Added mapper unit tests, repository slice tests (`@DataJpaTest` + Flyway), Spring Boot service tests (H2 + Flyway), and Postgres-backed Testcontainers integration tests for all three services.
  - Authored ADR-0024 documenting the persistence implementation and profile strategy.
- **Legacy singleton regression guard**:
  - Added `LegacySingletonUsageTest` to fail the build if `ContactService.getInstance()`, `TaskService.getInstance()`, or `AppointmentService.getInstance()` appear outside the approved legacy compatibility tests.

### Added
- **JacksonConfig.java** for strict JSON type enforcement (ADR-0023):
  - Disables Jackson's default type coercion that silently converts boolean/number values to strings.
  - `{"address": false}` now returns 400 Bad Request instead of being coerced to `"false"`.
  - `{"description": 123}` now returns 400 Bad Request instead of being coerced to `"123"`.
  - Ensures OpenAPI schema compliance for Schemathesis API fuzzing.
- **ADR-0023**: Documents API fuzzing findings, fixes, and known limitations.

### Fixed
- **Undocumented 400 status codes in OpenAPI spec**:
  - Added `@ApiResponse(responseCode = "400", description = "Invalid ID format")` to GET/{id} and DELETE/{id} endpoints in all controllers.
  - Schemathesis now validates that path variable validation errors are properly documented.
- **Path variable validation now enforced at runtime**:
  - Added `@Validated` annotation to `ContactController`, `TaskController`, and `AppointmentController`.
  - Without `@Validated`, Spring ignores `@Size` and other Bean Validation constraints on `@PathVariable` parameters.
  - The `@NotBlank @Size(min=1, max=MAX_ID_LENGTH)` constraints on `{id}` path variables are now actually enforced.
  - Added `ConstraintViolationException` handler to `GlobalExceptionHandler` for proper error responses when path variable validation fails.
  - Fixed import order in all three controllers to satisfy Checkstyle rules (static imports last, no extra blank lines).

### Added
- **CustomErrorController** for production-grade JSON error responses (ADR-0022):
  - Implements Spring Boot's `ErrorController` interface to intercept `/error` path.
  - Ensures ALL errors return `application/json`, including Tomcat-level errors (malformed requests, invalid paths) that bypass Spring MVC's `@RestControllerAdvice`.
  - Provides user-friendly error messages based on HTTP status (400, 404, 405, 415, 500).
  - Disabled Tomcat's whitelabel error page via `server.error.whitelabel.enabled=false`.
  - Added `@Hidden` annotation to exclude `/error` from OpenAPI spec (prevents Schemathesis from testing it as a regular API).
  - Added 17 unit tests in `CustomErrorControllerTest` for status codes, JSON content type, and message mapping.
  - Added 17 unit tests in `JsonErrorReportValveTest` for valve behavior (Content-Length, buffer reset, committed response guards, status code mapping).
- **JsonErrorReportValve** for Tomcat-level JSON error handling (ADR-0022):
  - Custom Tomcat valve intercepts errors at the container level BEFORE they reach Spring MVC.
  - Combined with `CustomErrorController`, creates a two-layer solution ensuring most error responses return `application/json`.
  - **Fixed chunked encoding issues** with explicit Content-Length: valve now guards with `isCommitted()`, resets buffer, sets explicit `Content-Length`, and writes bytes directly via `OutputStream` (standard Tomcat pattern: guard → reset → set headers → write bytes → flush).
  - Note: Extremely malformed URLs (invalid Unicode) fail at Tomcat's connector level before the valve, so `content_type_conformance` check is not used.
  - **All Schemathesis phases now pass** (Coverage, Fuzzing, Stateful): 30,668+ test cases generated, 0 failures.
- **Phase 2.5 complete**: API security testing foundation implemented.
- **Path variable validation** on controllers:
  - Added `@NotBlank @Size(min=1, max=MAX_ID_LENGTH)` to all `{id}` path parameters to reject whitespace-only and enforce 10-char limit.
  - Added `@Parameter(schema=@Schema(minLength=1, maxLength=10, pattern=".*\\S.*"))` for proper OpenAPI spec documentation.
  - Eliminates Schemathesis "schema validation mismatch" warnings by documenting actual API constraints.
  - Added `@Schema(pattern = ".*\\S.*")` to all `@NotBlank` DTO fields to reject whitespace-only strings in OpenAPI schema.
- **OpenAPI spec improvements** for better tooling compatibility:
  - Added `@Tag`, `@Operation`, `@ApiResponses` annotations to all controllers.
  - Changed content type from `*/*` to `application/json` via `produces`/`consumes` attributes.
  - Documented error responses (400, 404, 409) with `ErrorResponse` schema in OpenAPI.
  - Added operation summaries for Swagger UI clarity.

### Fixed
- **Schemathesis v4+ compatibility fixes** (multiple deprecated options removed):
  - Removed `--junit-xml` flag (removed in v4+, just capture stdout instead).
  - Removed `--base-url` flag (v4+ infers from spec URL).
  - Removed `--hypothesis-*` flags (removed in v4+).
  - Fixed `--checks` syntax: each check must be passed via its own flag, not comma-separated.
  - Updated both `.github/workflows/api-fuzzing.yml` and `scripts/api_fuzzing.py` to match.
- **API fuzzing date format mismatch resolved** (CI fuzzing failure root cause):
  - `AppointmentResponse` now uses same `@JsonFormat` pattern as `AppointmentRequest`: `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` with UTC timezone.
  - Previously, request accepted millis+offset but response omitted them, causing Schemathesis schema violations.
- **CodeQL unused parameter warnings resolved**:
  - `GlobalExceptionHandler.handleMalformedJson`: Added `@SuppressWarnings` with documentation explaining the parameter is required by Spring's `@ExceptionHandler` contract but intentionally unused to prevent information disclosure.
  - `ContactControllerTest`, `TaskControllerTest`, `AppointmentControllerTest`: Added `@SuppressWarnings` for `fieldName` parameters used in `@ParameterizedTest` display names via `{0}` placeholder.
- **Documentation audit fixes**:
  - `agents.md`: Updated ADR range from ADR-0014–0020 to ADR-0014–0021, updated current state to Phase 2.5 complete.
- **API fuzzing checks refined** to avoid false positives:
  - Changed from `--checks all` to specific checks: `not_a_server_error`, `response_schema_conformance`.
  - Not using `content_type_conformance` due to Tomcat connector-level URL decoding limitation (see ADR-0022).
  - Avoids flagging expected 400 responses (e.g., past dates rejected by `@FutureOrPresent`).
- **API fuzzing workflow hardened** (CodeRabbit review findings):
  - Added explicit `pyyaml` installation so YAML export succeeds reliably instead of silently failing.
  - Replaced fragile `grep -q '"status":"UP"'` health check with robust `jq -e '.status=="UP"'` JSON parsing.
  - Added JAR file validation before app startup: verifies exactly one JAR exists in `target/`, fails fast with clear error otherwise.
- **Dependency vulnerabilities resolved**:
  - Upgraded springdoc-openapi from 2.7.0 to 2.8.7 to fix CVE-2025-26791 (DOMPurify in swagger-ui).
  - Added explicit commons-lang3 3.20.0 dependency to override Spring Boot's 3.17.0 and fix CVE-2025-48924.
  - Overrode transitive commons-compress to 1.26.2 to remediate CVE-2024-25710 / CVE-2024-26308 reported by OWASP Dependency-Check.
- **SpotBugs issues fixed (7 issues → 0)**:
  - `AppointmentRequest`/`AppointmentResponse`: Added compact constructors and overridden accessors for defensive Date copies.
  - Controllers: Added `@SuppressFBWarnings` for intentional Spring DI singleton service storage (false positive).

### Changed
- `ApplicationTest.mainMethodCoverage` now launches `Application` with `--server.port=0` so PIT/CI runs pick an ephemeral port instead of colliding with another JVM on the agent.
- README/REQUIREMENTS/agents/Roadmap now document the expanded **319-test** suite and PIT's **96% mutation / 93% line coverage** after the new regression tests.
- Simplified legacy singleton compatibility: `getInstance()` in Contact/Task/Appointment services now returns the Spring-managed proxy when the context is ready (or the in-memory fallback before boot) without any manual `Advised` proxy unwrapping. Updated the corresponding Spring Boot service tests to assert shared persistence behavior between DI and legacy access instead of relying on brittle object identity checks.
- Updated `docs/logs/backlog.md` to mark CVE dependencies as fixed.

### Added (continued)
- **Phase 2.5 complete** (continued):
  - Added `.github/workflows/api-fuzzing.yml` for Schemathesis API fuzzing.
  - Workflow starts Spring Boot app, waits for health check, runs Schemathesis against `/v3/api-docs`.
  - Checks: all (5xx errors, schema violations, response validation).
  - Exports OpenAPI spec to `target/openapi/openapi.json` as CI artifact for ZAP and other tools.
  - Publishes JUnit XML results (`target/schemathesis-results.xml`) and summary to GitHub Actions.
  - Added `scripts/api_fuzzing.py` helper script for local API fuzzing runs.
  - Updated CI/CD plan Phase 9 to complete status.
- **Phase 2 complete**: REST API + DTOs implemented.
  - Added REST controllers: `ContactController`, `TaskController`, `AppointmentController` at `/api/v1/{resource}`.
  - Created request/response DTOs with Bean Validation (`ContactRequest`, `TaskRequest`, `AppointmentRequest`, etc.).
  - DTO constraints use static imports from `Validation.MAX_*` constants to stay in sync with domain rules.
  - Added `GlobalExceptionHandler` with `@RestControllerAdvice` mapping exceptions to HTTP responses (400, 404, 409).
  - Added custom exceptions: `ResourceNotFoundException`, `DuplicateResourceException`.
  - Added springdoc-openapi dependency; Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs`.
  - Added 71 controller tests (30 Contact + 21 Task + 20 Appointment) covering:
    - Happy path CRUD operations
    - Bean Validation boundary tests (exact max, one-over-max)
    - 404 Not Found scenarios
    - 409 Conflict (duplicate ID)
    - Date validation (past date rejection for appointments)
    - Malformed JSON handling
  - Created ADR-0021 documenting the REST API implementation decisions.
- **Service encapsulation**: Controllers now use service-level lookup methods instead of `getDatabase()`.
  - Added `getAllContacts()`, `getContactById(String)` to `ContactService`.
  - Added `getAllTasks()`, `getTaskById(String)` to `TaskService`.
  - Added `getAllAppointments()`, `getAppointmentById(String)` to `AppointmentService`.
  - All lookup methods return defensive copies and validate/trim IDs before lookup.
  - Controllers use `service.getAllXxx()` and `service.getXxxById(id)` for better encapsulation.
- **Exception handler tests**: Added `GlobalExceptionHandlerTest` with 4 unit tests for direct handler coverage.
  - Tests `handleIllegalArgument`, `handleNotFound`, `handleDuplicate` methods directly.
  - Ensures 100% mutation coverage for `GlobalExceptionHandler`.
- **Service lookup method tests**: Added 13 new tests for the service-level lookup methods.
  - `ContactServiceTest`: 7 tests for `getContactById()` and `getAllContacts()`.
  - `TaskServiceTest`: 6 tests for `getTaskById()` and `getAllTasks()`.
  - Total: 261 tests passing with 100% mutation score (159/159 killed).
- **Static analysis fixes**: Addressed CodeRabbit review findings.
  - Fixed ADR-0020 inconsistency where line 52 still referenced Phase 2 as future work.
  - Added `Objects.requireNonNull()` null checks to all Response DTO factory methods (`ContactResponse.from()`, `TaskResponse.from()`, `AppointmentResponse.from()`).
  - Updated parameterized validation tests to actually use the `expectedMessageContains` parameter for assertion (was unused).
  - Changed controller test `setUp()` methods to use autowired service instance instead of `getInstance()` for consistency with Spring DI.
  - Added CS320 spec comment to `Validation.java` constants explaining why limits follow assignment requirements.
  - Added deferred suggestions (service encapsulation, java.time migration, custom exceptions) to `backlog.md`.
- **DTO validation message improvements**: Replaced hardcoded values with Bean Validation placeholders.
  - All `@Size` annotations now use `{min}` and `{max}` placeholders instead of hardcoded numbers.
  - Updated `ContactRequest`, `TaskRequest`, `AppointmentRequest` to use dynamic message interpolation.
  - This ensures validation messages stay accurate if field constraints change.
- **AppointmentRequest date format fix**: Aligned Javadoc with actual `@JsonFormat` pattern.
  - Updated `@JsonFormat` pattern to `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` (ISO 8601 with millis and offset).
  - Javadoc now correctly documents the accepted format.
- **AppointmentControllerTest robustness**: Replaced brittle date string handling with DateTimeFormatter.
  - Uses `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC)` for consistent date formatting.
  - Eliminates fragile `substring(0, 19)` approach.
- **ContactServiceTest clarity**: Added explicit `isPresent()` assertions before `get()` on Optionals.
  - Makes test intent clearer and produces better failure messages.
- **Phase 1 complete**: Spring Boot scaffold implemented.
  - Added Spring Boot 3.4.12 parent POM with starter-web, actuator, and validation dependencies.
  - Created `Application.java` Spring Boot entrypoint with `@SpringBootApplication`.
  - Reorganized packages into layered architecture: `contactapp.domain`, `contactapp.service`, `contactapp.api`, `contactapp.persistence`.
  - Added `@Service` annotations to `ContactService`, `TaskService`, and `AppointmentService` while preserving `getInstance()` for backward compatibility.
  - Made `copy()` methods public so services can access them across packages.
  - Created `application.yml` with profile-based configuration (dev/test/prod) and actuator lockdown (health/info only).
  - Added Spring Boot smoke tests: `ApplicationTest` (context load), `ActuatorEndpointsTest` (endpoint security verification), `ServiceBeanTest` (bean presence and singleton verification).
  - All tests pass, 100% mutation score maintained.
- **100% instruction and branch coverage achieved**: Added tests and removed unreachable code.
  - Added `testGetInstanceColdStart` to `ContactServiceTest`, `TaskServiceTest`, and `AppointmentServiceTest` using reflection to reset the static instance and verify lazy initialization.
  - Added `mainMethodCoverage` to `ApplicationTest` to exercise the `Application.main()` method directly.
  - Converted `testCopyRejectsNullInternalState` to parameterized tests covering all null field branches.
  - Removed unreachable `source == null` checks from `validateCopySource()` methods (copy() passes `this`, which can never be null).
  - Total: 173 tests passing with 100% instruction and branch coverage.
- **Code review hardening**: Addressed recommendations from comprehensive code review.
  - Documented UTC timezone behavior in `Validation.validateDateNotPast()` Javadoc so callers understand that "now" is evaluated in UTC.
  - Made `clearAllContacts()`, `clearAllTasks()`, and `clearAllAppointments()` package-private to prevent accidental production usage while still allowing test access (tests are in the same package).
  - Added phone internationalization idea to `docs/logs/backlog.md` for Phase 2+.
- **Phase 0 complete**: Defensive copies and date validation fix implemented.
  - Added `copy()` methods to `Task` and `Contact` classes (matching `Appointment` pattern).
  - Updated `TaskService.getDatabase()` and `ContactService.getDatabase()` to return defensive copies, preventing external mutation of internal state.
  - Fixed `Validation.validateDateNotPast()` to accept equality (date equal to "now" is valid), avoiding millisecond-boundary flakiness.
  - Added `testGetDatabaseReturnsDefensiveCopies` tests to `TaskServiceTest` and `ContactServiceTest`.
  - Updated existing tests to verify data by field values instead of object identity.
  - Added Mockito mock-maker subclass configuration (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) so Spring Boot tests run on JDK 25 without agent attach.
- **Mutation coverage improved to 100%**: Added tests to kill all surviving mutants.
  - Added `testCopyRejectsNullInternalState` to `TaskTest` and `ContactTest` to kill mutants where `validateCopySource()` calls could be removed.
  - Added `validateDateNotPastAcceptsDateExactlyEqualToNow` to `ValidationTest` using `Clock.fixed()` to deterministically test the boundary mutant (`<` vs `<=`).
  - Added `privateConstructorIsNotAccessible` to `ValidationTest` for line coverage of the utility class private constructor.
  - All tests pass, 100% mutation score, 100% line coverage.
- **Code review fixes**: Addressed issues identified during pre-Phase-1 code review.
  - Refactored `ContactService.updateContact()` and `TaskService.updateTask()` to use `computeIfPresent()` for thread-safe atomic lookup and update.
  - Added `final` modifier to `Contact` class (matching `Task` and `Appointment`).
  - Normalized singleton `volatile` usage: removed from `AppointmentService` since `getInstance()` is `synchronized`.
  - Renamed `validateNumeric10` to `validateDigits` for clarity (method accepts `requiredLength` parameter).
  - Added `Clock` parameter overload to `validateDateNotPast()` for testable time injection.
- Added validation layer improvement ideas to `docs/logs/backlog.md` for Phase 2+ (TimeProvider, domain wrappers, pattern/range helpers, ValidationException, Bean Validation bridge).
- Consolidated application requirements/roadmap/checklist into `docs/REQUIREMENTS.md` (phases 0–10) and `docs/ROADMAP.md` (overview).
- Phase 0 (Pre-API Fixes) captured in `docs/REQUIREMENTS.md`: defensive copies for Task/ContactService, date validation boundary, copy() methods for Task/Contact.
- Published ADR-0014 through ADR-0019 covering backend stack, database, API style, frontend stack, auth model, and deployment/packaging; updated ADR index accordingly.
- Added `agents.md` quick-link guide for assistants/automation to roadmap/requirements/ADRs.
- Appointment domain/service documentation and tests:
  - `docs/adrs/ADR-0012-appointment-validation-and-tests.md` and `docs/adrs/ADR-0013-appointmentservice-singleton-and-crud-tests.md`.
  - README/index updates for Appointment classes/tests and ADR index through ADR-0013.
  - Expanded README sections with validation/service mermaid diagrams and scenario coverage for appointments.
  - AppointmentService/AppointmentServiceTest implementation and coverage (singleton, add/duplicate/null, delete/blank/missing, update/trim/blank/missing, clear-all).
- README command cheat sheet plus a CI “Jobs at a Glance” table so common tasks and pipeline behavior are easy to follow without losing detail.
- Clarified README validation diagrams (trimmed text fields, phone digits-only) and documented the container verify job alongside the optional self-hosted mutation lane.
- Documented Sonatype OSS Index setup in `README.md` so dependency scans can be
  fully authenticated when desired.
- Introduced `scripts/ci_metrics_summary.py` and a GitHub Actions summary step
  so each matrix run publishes tests/coverage/mutation/dependency metrics.
- Added a "Sample QA Summary" section to `README.md` showing what the Actions
  summary table looks like for a representative run.
- Created `docs/logs/backlog.md`, moving the detailed
  backlog/sample content out of the README to keep it focused.
- Introduced `Contact#update(...)` so multi-field updates validate every value
  before committing changes, preventing partially updated contacts.
- Published the Task entity/service implementation plan in
  `docs/architecture/2025-11-19-task-entity-and-service.md` and added the ADR
  catalog (`docs/adrs/README.md`) plus ADR-0001 through ADR-0008 to capture
  validation, storage, atomic update, CI, SpotBugs runtime, documentation, Task
  feature choices, and the CI metrics summary script.
- Added `scripts/serve_quality_dashboard.py` (packaged with the `quality-reports-*`
  artifact) so contributors can spin up a local HTTP server to view the React QA dashboard.
- Added personal design notes under `docs/design-notes/notes/` (CI gates, metrics script,
  Contact entity/service behaviors, SpotBugs plan, docs structure, and test strategy) to
  capture the reasoning behind each piece of the implementation.
- Added Shields.io badges for JaCoCo line coverage, PITest mutation score, SpotBugs status,
  and Dependency-Check findings at the top of the README, plus a license badge for quick
  at-a-glance metadata.
- Updated README badges to the bolder “for-the-badge” style with GitHub/Codecov logos so the status bar matches the secure snapshot look-and-feel; static-analysis badges now use the same passing green and point to this repository.
- Checked off every item in `docs/requirements/task-requirements/requirements_checklist.md` now that the Task entity/service and tests are implemented.
- Finalized `docs/architecture/2025-11-19-task-entity-and-service.md` (status Implemented, summary, DoD results, deviations) so the Task design record reflects the delivered code.
- Dependabot Maven job now runs daily instead of weekly so dependency updates land faster.
- Bumped `org.pitest:pitest-maven` to 1.22.0 plus SpotBugs dependencies (`spotbugs-annotations` 4.9.8 and `spotbugs-maven-plugin` 4.9.8.1) to keep the QA toolchain current.
- Published ADR-0009 describing the permanent unit-test strategy (layered test classes,
  AssertJ + parameterized tests, singleton reset helpers, and CI enforcement via JaCoCo/PITest).
- Added appointment architecture/ADRs plus tightened appointment validation (trim-before-validate, not-blank guard),
  ID trimming in service adds, date validation reuse in `update`, and time-stable appointment tests (relative dates).

### Fixed
- **Spring DI and getInstance() now share the same backing store**: Made the `ConcurrentHashMap` database static in all services (`ContactService`, `TaskService`, `AppointmentService`). This guarantees Spring-created beans and legacy `getInstance()` callers share the same data regardless of how many instances are created or which access path runs first. Previously, each instance had its own map, so data added through one path was invisible to the other.
- **Code review documentation fixes**:
  - Removed incorrect "test" profile claim from `ApplicationTest.java` Javadoc (no `@ActiveProfiles` was present).
  - Removed "constructor injection preferred" from `ServiceBeanTest.java` Javadoc since it uses field injection.
  - Added missing `ValidationTest` coverage for `validateDigits`: happy path, non-digit rejection, wrong length rejection.

### Changed
- README/index/agents now link to `docs/REQUIREMENTS.md`, `docs/ROADMAP.md`, and `docs/INDEX.md`.
- ADR-0018 expanded to document secrets management (env vars for dev/test; vault/secret manager for prod).
- Backlog now tracks deferred decisions (UI kit, OAuth2/SSO, WAF, prod secrets).
- Appointment is now `final`, `Appointment.copy()` validates source state and reuses the public constructor for defensive copies, and `AppointmentServiceTest` uses reflection (instead of subclassing) to simulate a blank ID so SpotBugs remains clean.
- Documented the shared `validateDateNotPast` helper across README/index/ADR/design notes and expanded `ValidationTest` with future/past-date cases so Appointment docs mirror the code.
- Appointment update/add logic now validates both fields before mutation, enforces trimmed IDs on add,
  and uses `computeIfPresent` for updates; README/architecture/design notes updated accordingly.
- AppointmentService normalizes IDs across CRUD and returns an unmodifiable snapshot of defensive copies to prevent external mutation.
- Simplified CI flow diagram labels for reliable GitHub mermaid rendering.
- Relocated the README command cheat sheet under the CI/CD section for better flow while keeping all commands visible.
- ADR-0007 (Task Entity/Service) and ADR-0009 (Test Strategy) are now marked Accepted in the ADR index and corresponding files.
- Java CI workflow now installs Python 3.12 for every matrix leg so the QA
  summary script runs reliably on Windows and Ubuntu runners.
- CodeQL workflow now pins Temurin JDK 17, caches Maven deps, enforces
  concurrency/path filters, and runs the `+security-and-quality` query pack via
  CodeQL autobuild for broader coverage.
- Java CI’s Codecov upload now reads the token from the job env to remain
  parseable for forked PRs where the `secrets` context is unavailable.
- Java CI explicitly passes `-DnvdApiKey`/delay flags to Maven verify so the
  built-in Dependency-Check execution always receives the configured secret.
- Maven Compiler now uses `<release>17</release>` via `maven.compiler.release`
  to align with the module system and eliminate repeated “system modules path”
  warnings during `javac`.
- Added `ValidationTest` to assert boundary acceptance and blank rejection in
  helper methods, giving PIT visibility into those behaviors.
- Extended `ValidationTest` with explicit null-path checks so the helper’s
  `validateNotBlank` logic triggers before length/numeric math.
- Removed redundant `validateNotBlank` calls from `Contact` so setters and the
  constructor rely solely on the shared `Validation` helpers (eliminating the
  equivalent PIT mutants and enabling a 100% mutation score).
- Contact setters now trim first/last names and addresses after validation to
  avoid persisting accidental leading/trailing whitespace.
- Contact IDs are now trimmed on construction, and `Validation.validateLength`
  measures trimmed length so validation matches the data actually stored.
- Expanded Checkstyle configuration (imports, indentation, line length, braces, etc.)
  and wired `spotbugs-maven-plugin` (auto-skipped on JDK 23+) so bug patterns fail
  `mvn verify`.
- `ContactService#getDatabase()` now returns an unmodifiable snapshot of defensive
  copies (via `Contact.copy()`) and a new `clearAllContacts()` helper was added for
  tests, eliminating SpotBugs exposure warnings for the service internals.
- `ContactService#updateContact` now routes through the atomic
  `Contact#update(...)` helper so updates either fully apply or leave the contact
  unchanged while reusing the constructor’s validation messages.
- Expanded service tests to cover singleton reuse, missing delete branch, and a
  last-name change during updates so mutation testing can kill the remaining
  ContactService mutants.
- Fixed the Java CI workflow so `dependency.check.skip` uses the correct
  hyphenated property, Codecov upload keys off `secrets.CODECOV_TOKEN`, and
  quality-report artifacts no longer fail the job when reports are absent.
- Updated `README.md` and `docs/index.md` to match the new `docs/requirements`
  structure and clarify when the optional self-hosted mutation lane is used.
- Scheduled Dependabot’s weekly Maven check to run Mondays at 04:00 ET so
  updates happen predictably.
- README now documents why the `release-artifacts` job is skipped on PRs and
  tracks upcoming reporting enhancements.
- Added Codecov integration (GitHub Action upload + README instructions/badge).
- Clarified README sections describing the `ConcurrentHashMap<String, Contact>`
  storage, the defensive-copy snapshot pattern, and the atomic update helper
  managed by `ContactService`.
- Corrected README documentation to state that SpotBugs currently runs on supported
  JDKs (17/21) instead of implying it auto-skips on newer runtimes.
- Enhanced `scripts/ci_metrics_summary.py` to show colored icons/bars, severity
  breakdowns, and generate a dark-mode `target/site/qa-dashboard/index.html`
  page with quick links to JaCoCo/SpotBugs/Dependency-Check/PITest reports.
- React-based QA dashboard (`ui/qa-dashboard`) now builds during CI, consumes the
  generated `metrics.json`, and replaces the old static HTML so artifacts ship a
  fully interactive console.
- Added a README note explaining why the service methods return `boolean`
  (simple success/failure signaling for this milestone).
- Clarified Task test documentation (TaskTest Javadocs, README/index, and test design notes) to call out invalid update coverage and atomicity checks.
- Updated README caching section to explain that Dependency-Check caches are
  intentionally purged each run (only Maven artifacts remain cached).
- CI flow diagram now includes the QA summary/Codecov step so the visual matches
  the workflow description.
- Restructured the README’s testing section so `ContactTest` and
  `ContactServiceTest` each have their own bullet list explaining scope and
  assertions.
- Created dedicated README sections for `ContactService` validation/testing with
  TODO placeholders so the service mirrors the structure already documented for
  `Contact`.
- Added an over-length guard test in `ValidationTest` to cover the max-length branch and keep validation mutation/branch coverage at 100%.
- Clarified README headings/TOC so each section explicitly references the source
  file (`Contact.java`, `ContactTest.java`, `ContactService.java`,
  `ContactServiceTest.java`).
- Validation section now explicitly links to `Validation.java` so readers know
  which helper backs the Contact rules.
- `ContactService` validation headings now link to the source file so both
  sections mirror each other.
- Converted the Validation helper docs to describe all domain fields (Contact + Task) now that both models share the same utility.
- Added README links in each subheading so readers can jump directly to the
  relevant file (`Contact.java`, `ContactTest.java`, `ContactService.java`,
  `ContactServiceTest.java`) from the design/testing sections.
- Added explicit placeholders/doc comments in `ContactService.java` and
  `ContactServiceTest.java` so the service layer mirrors the structure of the
  `Contact`/`ContactTest` pair while we flesh out CRUD behavior.
- Completed the README sections for `Task.java`/`TaskTest.java` and `TaskService.java`/`TaskServiceTest.java`, describing validation flow, error philosophy, and scenario coverage instead of TODO placeholders.
- README now matches the implementation details: `ContactService.updateContact` references `Contact.update(...)`, `validateDigits` lists the `requiredLength` parameter, and SpotBugs/JDK matrix text reflects the actual `{17, 21}` CI coverage.
- Documented the new `ValidationTest.validateLengthRejectsTooLong` scenario in README to reflect full length-check coverage.
- README badges now use a uniform Shields.io flat-square style (GitHub Actions, Codecov, JaCoCo, PITest, SpotBugs, OWASP DC, License) with consistent colors (brightgreen for CI/coverage/mutation, blue for static analysis/license).
- Added `TaskServiceTest.testClearAllTasksRemovesEntries` (with a note explaining it) so PIT kills the last surviving mutant that removed the internal `Map.clear()` call.
- Captured the Contact/Task invalid-update atomicity tests in the README scenario coverage lists so users can see those guardrails.
- `ContactService#updateContact` and `TaskService#updateTask` now validate/trim
  the incoming ids (matching the delete paths) so lookups succeed even if
  callers include whitespace, and both services now throw `IllegalArgumentException`
  for blank ids on update; new unit tests cover the trimmed-id success path and
  blank-id error case.
- Expanded `TaskTest.invalidUpdateInputs` with empty-string and null cases to mirror constructor/setter validation, and clarified README Dependency-Check defaults (3500ms with an API key, 8000ms without).
- Refreshed `README.md` and `index.md` to correct Task file paths (now under
  `contactapp`), link to the new architecture/ADR directories, and fix stale
  relative links left over from the original docs layout.
- Updated `ui/qa-dashboard` to emit relative asset paths, fixed report links,
  and documented the new `serve_quality_dashboard.py` helper so the downloaded
  dashboard renders correctly when opened locally or via the bundled server.
- Extended `scripts/ci_metrics_summary.py` so Dependency-Check uses the shared
  `target/` constant, PITest "detected" counts flow into summaries, and the QA
  report now includes a dependency severity breakdown row plus ADR tracking.
- Enhanced `scripts/ci_metrics_summary.py` to optionally write badge JSON for JaCoCo,
  PITest, SpotBugs, and Dependency-Check when `UPDATE_BADGES=true`, so badges stay in sync.

### Security
- Bumped `org.pitest:pitest-maven` to `1.21.1` and `org.pitest:pitest-junit5-plugin`
  to `1.2.3` for the latest mutation-testing fixes.
- Upgraded `org.owasp:dependency-check-maven` to `12.1.9` for the most recent
  CVE feed handling improvements.
