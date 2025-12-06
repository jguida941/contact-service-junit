# Changelog
All notable changes to this project will be documented here. Follow the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) spirit and use
[Semantic Versioning](https://semver.org/) once formal releases begin.

## [Unreleased]

### Fixed
- **CodeRabbit Audit Fixes (2025-12-06)**:
  - ADR-0050: Changed status from "Implemented" to "Partially Implemented" (Task reconstitute() pending)
  - Added JavaDoc to deprecated `findAll()` methods explaining user isolation bypass and ADR-0054 reference
  - AuthControllerUnitTest: Added `@AfterEach` for SecurityContextHolder cleanup (prevents test pollution)
  - cs script: Fixed to use SCRIPT_DIR for module resolution from any directory
  - ProjectsPage: Added `isPending` guard to `handleQuickArchive` (prevents duplicate API calls)
  - AppointmentsPage: Update `selectedAppointment` on archive/unarchive mutation success (prevents stale state)
  - PROJECT_SHOWCASE.md: Standardized mutation score to 84% (was inconsistent 80%/85%)

### Testing
- **Mutation Testing Coverage Improvements (2025-12-06)**:
  - Added 40+ targeted tests to TaskServiceValidationTest.java to kill mutations in InMemoryStore legacy paths
  - Fixed InMemoryTaskStore.save() to support update operations (was only supporting inserts)
  - TaskService mutation coverage improved from 39% to 77%
  - Overall project mutation coverage improved from 80% to 84%
  - Test strength improved to 94% (number of mutants killed / total mutants)

### Documentation
- **CSRF/SameSite Documentation Accuracy (2025-12-06)**:
  - Updated ADR-0054 with nuanced CSRF configuration details (explicit config vs `.spa()`, both valid)
  - Clarified that `Rfc6265CookieProcessor` requires explicit `setSameSiteCookies()` call for SameSite enforcement
  - Documented alternative approaches: `SpaCsrfTokenRequestHandler`, `.spa()`, per-cookie SameSite (all equally valid)
  - Updated security-infrastructure-notes.md with accurate CSRF section and `frame-ancestors 'none'` CSP
  - Updated README Security Infrastructure: fixed year to 2025, X-Frame-Options to DENY, accurate CSRF description
  - Added trade-off analysis: container-wide SameSite is defense-in-depth but adds complexity

- **Documentation Consistency Update (2025-12-06)**:
  - Updated ADR-0050, ADR-0051, ADR-0052 status from "Accepted" to "Implemented"
  - Updated docs/INDEX.md with new security files (RefreshToken, TokenFingerprintService, SchedulingConfig)
  - Updated security-infrastructure-notes.md with all ADR-0052 phases
  - Updated ROADMAP.md with ADR-0052 production auth system completion
  - Added ADR-0053 (Timezone-Safe Date Parsing) to ADR README
  - All documentation now consistent with 1109 tests, 90% line coverage, 85% mutation score

### Added
- **Task Archive Functionality (2025-12-06)**:
  - Added `archived` boolean field to Task domain, entity, and mapper
  - Added `archiveTask()` and `unarchiveTask()` methods to TaskService
  - Added `PATCH /api/v1/tasks/{id}/archive` and `/unarchive` endpoints to TaskController
  - Added `archived` field to TaskResponse DTO
  - Added V18 migration: `ALTER TABLE tasks ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE`
  - Frontend API: Added `tasksApi.archive()` and `tasksApi.unarchive()` methods
  - Table actions: Added archive icon button with ARCHIVED badge
  - "Show Archived" toggle to display archived tasks

- **Project Quick Archive Button (2025-12-06)**:
  - Added one-click archive/unarchive button to Projects table row actions
  - Clicking archive sets project status to ARCHIVED, unarchive sets to ACTIVE
  - Added archive button to project detail view
  - Uses existing ProjectStatus.ARCHIVED enum value

- **Appointment Archive UI (2025-12-06)**:
  - Added manual archive/unarchive functionality to Appointments page
  - Frontend API: Added `appointmentsApi.archive()` and `appointmentsApi.unarchive()` methods
  - Table actions: Added archive icon button (toggles between Archive/Unarchive)
  - Detail view: Added "Archive" / "Unarchive" button
  - Uses existing backend endpoints: `PATCH /api/v1/appointments/{id}/archive` and `/unarchive`
  - Archive icon (📦) archives appointment, Unarchive icon (📤) restores it
  - "Show Archived" toggle displays archived appointments with gray "ARCHIVED" badge

### Fixed
- **CORS Configuration Fix (2025-12-06)**:
  - Added `PATCH` to allowed HTTP methods in SecurityConfig CORS configuration
  - Fixed 403 Forbidden error on archive/unarchive PATCH requests from frontend
  - Was missing from `configuration.setAllowedMethods()` list

- **ADR-0053: Timezone-Safe Date Parsing (2025-12-06)**:
  - Fixed critical bug where task due dates displayed one day earlier in negative UTC offset timezones
  - Root cause: `new Date("2025-12-06")` parses as UTC midnight, shifts backwards when displayed in EST/PST
  - Solution: Use `parseISO()` from date-fns which parses as local midnight
  - Created `ui/contact-app/src/lib/dateUtils.ts` with centralized date utilities:
    - `formatDateSafe()` - formats date-only strings correctly
    - `formatDateTimeSafe()` - formats datetime strings correctly
    - `parseDateSafe()` - parses dates for manipulation
  - Added timezone regression tests in `dateUtils.test.ts`
  - See ADR-0053 for full analysis and prevention guidelines

### Security
- **Comprehensive Security Audit (ADR-0054) (2025-12-06)**:
  - **H-2 Fixed**: Added `jwt.require-fingerprint` config flag to enforce token fingerprint binding
  - **M-1 Fixed**: Added JTI (JWT ID) claim to all tokens for revocation support
  - **M-6 Fixed**: Added `API:` prefix to user-based rate limit bucket keys
  - **M-8 Fixed**: Deprecated `findAll()` methods that bypass user isolation with `@Deprecated(since="1.0", forRemoval=true)`
  - **L-1 Fixed**: Replaced `alert()` calls with toast notifications in TasksPage and AppointmentsPage
  - Created ADR-0054 documenting all security audit findings and recommendations
  - See ADR-0054 for complete security audit report

- **CodeRabbit Security Audit Fixes (2025-12-06)**:
  - **PII Logging**: Changed `RefreshTokenService` to log user ID instead of username in 4 locations (GDPR/CCPA compliance)
  - **Null Safety**: Added null/empty parameter validation to 6 `RefreshTokenService` methods
  - **Missing FK Constraint**: Added `fk_tasks_assignee_id` foreign key in V16 migrations (H2 and PostgreSQL)
  - **Swagger in Production**: Disabled `springdoc.api-docs` and `springdoc.swagger-ui` in `application-prod.yml`
  - **Log Files**: Added `logs/` directory to `.gitignore`
  - **CSP frame-ancestors**: Fixed production CSP to use `frame-ancestors 'none'` (was `'self'`), now matches X-Frame-Options DENY
  - **Debug Logging**: Removed debug `console.log` statements from `AppointmentsPage.tsx`
  - **CLI Robustness**: Added error handling to `./cs db reset` and mutually exclusive flag validation to `./cs dev`
  - **SameSite Test**: `SecurityConfigIntegrationTest` now properly asserts `SameSite=Lax` attribute on CSRF cookie
  - **NaN Handling**: Added `parseInt` NaN validation in `api.ts` for corrupted sessionStorage values
  - **SameSite Docs**: Fixed `production-auth-system-plan.md` code examples to use `SameSite=Lax` (was incorrectly `Strict`)
  - **Migration Count**: Updated `PROJECT_SHOWCASE.md` migration count from 16 to 17, added V18 to table

- **ADR-0052 Batches 6-9: Refresh Tokens - PRODUCTION COMPLETE (2025-12-03)**:
  - Created `RefreshToken` entity with UUID PK, opaque token, expiry, revoked flag, optimistic locking
  - Created `RefreshTokenRepository` with queries for find, revoke, delete with UUID user IDs
  - Created `RefreshTokenService` with single-session model, token rotation, scheduled cleanup
  - Added V17 migrations (PostgreSQL and H2) for `refresh_tokens` table with proper indexes
  - Enabled `@EnableScheduling` via `SchedulingConfig` for hourly cleanup job
  - **AuthController integration**: login/register create refresh tokens and set cookies
  - **Token refresh**: validates refresh token, rotates both refresh and access tokens
  - **Logout**: revokes refresh token and clears all auth cookies
  - **Cookie security**: refresh token scoped to `/api/auth/refresh`, HttpOnly, SameSite=Lax
  - **Frontend**: stores absolute expiration timestamp for precise session checks on page reload
  - Added 17 unit tests for `RefreshTokenService` and 4 integration tests for logout/cookies
  - Completes ADR-0052 Phase B refresh token implementation

- **ADR-0052 Batch 4+5: Token Fingerprinting - PRODUCTION COMPLETE (2025-12-03)**:
  - Created `TokenFingerprintService` with secure fingerprint generation (50 bytes), SHA-256 hashing, and constant-time verification
  - Added `generateToken(UserDetails, String fingerprintHash)` overload to `JwtService`
  - Added `extractFingerprintHash(String token)` method to `JwtService`
  - Added fingerprint verification to `JwtAuthenticationFilter` with backwards compatibility
  - **Cookie names per ADR-0052**: `__Secure-Fgp` (HTTPS) and `Fgp` (HTTP dev)
  - **Cookie attributes per OWASP**: HttpOnly=true, Secure (HTTPS), SameSite=Lax, Path=/
  - **AuthController integration**: login/register/refresh generate fingerprints, set hardened cookies
  - **Logout**: clears both fingerprint cookie variants
  - Implements OWASP sidejacking prevention per ADR-0052 Phase C

- **ADR-0052 Batch 5: Fingerprint Call Site Migration (2025-12-03)**:
  - Deleted old `generateToken(UserDetails)` and `generateToken(Map, UserDetails)` adapter overloads from `JwtService`
  - Migrated `AuthController` login, register, and refresh endpoints to pass real fingerprint hashes
  - Updated 18+ test calls in `JwtServiceTest` to fingerprint-aware signature
  - Added fingerprint claim tests (`generateToken_includesFingerprintClaim`, `generateToken_withNullFingerprint_producesTokenWithoutFphClaim`)
  - Fixed fingerprint cookie max-age alignment with JWT expiration
  - Completes Phase C fingerprint implementation - all tokens now include fingerprint binding

- **ADR-0052 Batch 3: HTTPS Setup (2025-12-03)**:
  - Added `./cs setup-ssl` command to generate self-signed SSL keystore
  - Added `server.ssl.*` configuration block to `application.yml`
  - SSL disabled by default (`SSL_ENABLED=false`), opt-in for local HTTPS testing
  - Updated `.gitignore` for SSL keystores and certificates
  - Added HTTPS Development section to README with usage instructions

- **ADR-0052 Batch 2: UUID Cascade Fixes (2025-12-03)**:
  - Updated `UserRepository` methods to use `UUID` parameter types
  - Changed `Task.assigneeId` from `Long` to `UUID` (domain, entity, DTO, service, controller)
  - Updated `@WithMockAppUser` annotation from `long id()` to `String id()` (UUID string)
  - Fixed H2 migration V16 to drop projects unique constraint before column drop
  - Updated ActuatorEndpointsTest: 403 → 401 for unauthenticated requests
  - Updated JwtServiceTest for 60-second clock skew tolerance
  - All tests pass (949 tests)

- **ADR-0052 Batch 1: Phase 0 Critical Fixes (2025-12-03)**:
  - Migrated `users.id` from BIGINT to UUID via data-preserving V16 migration (PostgreSQL and H2)
  - Added `@JsonIgnore` on `User.password` field and getter to prevent hash leakage
  - Configured `AuthenticationEntryPoint` for 401 JSON responses with UTF-8 encoding
  - Configured `AccessDeniedHandler` for 403 JSON responses with UTF-8 encoding
  - Hardened JWT validation: 60-second clock skew, issuer (`contact-service`), audience (`contact-service-api`)
  - Fixed frontend 401 vs 403 handling: 403 no longer clears session
  - Changed UUID generation to portable `@PrePersist` pattern (CodeRabbit recommendation)
  - V16 migration preserves unique constraints from V6/V8 (contacts, tasks, appointments, projects)

### Changed
- **README Cleanup and Reorganization (2025-12-03)**:
  - Fixed stale metrics: 904→930 tests, ~83→84% mutation coverage, ~89.9→90% line coverage
  - Removed duplicate sections: React UI Highlights merged into React UI Layer Phase 4, duplicate Testcontainers Strategy removed
  - Condensed verbose Phase 5 Security and ADR-0045 summaries into brief "Key Capabilities" section with links
  - Fixed broken ToC entry for removed React UI Highlights section
  - Added PROJECT_SUMMARY.md with comprehensive project metrics and quick reference

### Fixed
- **docs/CI-CD Link Case Sensitivity (2025-12-03)**:
  - Fixed `docs/ci-cd` → `docs/CI-CD` in README, REQUIREMENTS.md, ADR-0043, docs-structure-notes.md
  - Git tracks directory as `CI-CD` (uppercase) but macOS shows `ci-cd` due to case-insensitive filesystem

### Added
- **Unassigned Tasks Filter (2025-12-03)**:
  - Added `GET /api/v1/tasks?projectId=none` to query tasks not linked to any project
  - Added `findByUserAndProjectIdIsNull()` to `TaskRepository`
  - Added `getUnassignedTasks()` to `TaskService`
  - Added `@Order(5)` to `RateLimitingFilter` for deterministic filter chain ordering

- **ADR-0050: Domain Reconstitution Pattern (2025-12-03)**:
  - Static reconstitute() factory methods on Appointment and Task domains
  - Enables loading past appointments/overdue tasks from database without validation failures
  - Maintains strict temporal validation for new API submissions
  - Appointment.isPast() computed property for UI indicators
  - Task.isOverdue() computed property for UI indicators

- **Appointment Archive Feature (2025-12-03)**:
  - Added `archived` boolean field to Appointment domain and entity
  - PATCH /api/v1/appointments/{id}/archive endpoint
  - PATCH /api/v1/appointments/{id}/unarchive endpoint
  - Frontend badges for past/archived appointments and overdue tasks
  - Database migration V14__add_archived_to_appointments.sql

- **Design Notes Reference Documentation (2025-12-03)**:
  - Added 7 design notes extracted from README to `docs/design-notes/notes/`
  - Includes: ci-cd-operations-notes, testing-strategy-notes, and others
  - Keeps README focused while preserving detailed operational knowledge

### Changed
- **Test profile split for legacy/singleton coverage (2025-12-02)**:
  - Main CI (Linux) continues to run the full suite with Testcontainers/Postgres and JaCoCo gates.
  - Windows/`skip-testcontainers` profile now runs service/controller suites on H2 (no Docker) to keep coverage meaningful on that runner; only `**/*IT.java` remain excluded.
  - Legacy `getInstance()` suites are tagged `legacy-singleton` and can be run separately via `mvn test -Plegacy-singleton` to avoid interfering with the main pipeline.

### Fixed
- **RateLimitingFilter log sanitizer helper restored (2025-12-02)**:
  - Reintroduced `getSafeLogValue` with whitelist/length guards and shared it across log helpers
  - Fixes `RateLimitingFilterTest` reflection coverage and keeps log injection defenses centralized
- **TestCleanupUtility transactional reset (2025-12-02)**:
  - Cleanup remains `REQUIRES_NEW`, and test user seeding now runs in its own `REQUIRES_NEW` transaction to avoid nested transaction side effects
  - `TestUserSetup` now uses `saveAndFlush` so FK targets exist before task inserts, preventing spurious FK violations in service tests

- **Parallel Test Execution Fix (2025-12-02)**:
  - Disabled JUnit Platform parallel execution to prevent singleton state collisions in @Isolated tests
  - Added `junit.jupiter.execution.parallel.enabled=false` to junit-platform.properties
  - Eliminates race conditions where parallel execution attempted to share singleton state across test classes despite @Isolated annotations
  - Total test count now at **1066** tests

- **Test Isolation via Centralized Cleanup Utility (2025-12-02)**:
  - Created `TestCleanupUtility` component in `contactapp.service` package to enforce proper test cleanup order
  - Resolved 8 `DuplicateResourceException` failures across service test suites (Appointment, Contact, Task, Project - both standard and Legacy tests)
  - Root cause: Static singleton instances persisted across tests while Spring Boot reused ApplicationContext, causing `registerInstance()` to migrate stale data into fresh stores
  - Solution: Reflection-based singleton reset BEFORE clearing data prevents migration of old test data
  - Cleanup order enforced: security contexts → singleton reset → user cleanup → service data clearing
  - All service tests now call `testCleanup.resetTestEnvironment()` in `@BeforeEach` for consistent isolation
  - Documented in new **ADR-0047** (Test Isolation and Cleanup Utility) with full architectural rationale
  - Updated ADR-0009 (Test Strategy) to reference centralized cleanup pattern
  - Tests now run reliably with all 1066 tests passing without order-dependent failures

### Added
- **TaskService Test Coverage Improvements (2025-12-02)**:
  - Added 44 new tests to TaskServiceTest bringing total from 29 to 73 tests
  - New coverage for null taskId validation, extended update overloads (5, 6, 7 params)
  - Query method tests: getTasksByStatus, getTasksDueBefore, getTasksByProjectId, getTasksByAssigneeId, getOverdueTasks
  - Defensive copy tests for all query methods ensuring returned lists are independent copies
  - User isolation tests verifying query methods only return current user's tasks
  - Test count updated to **1056** tests (up from 951)

### Changed
- **SpringBootTests now use Postgres via Testcontainers (2025-12-02)**:
  - Switched all Spring Boot service/controller/MockMvc suites to the `integration` profile backed by Postgres Testcontainers; H2 remains only for unit/slice tests.
  - Shared `PostgresContainerSupport` base class introduced to standardize container startup and `@ServiceConnection`.
  - Local/CI runs now require Docker for `mvn test`; use `-DskipITs=true -Dspring.profiles.active=test` for H2-only slices if Docker is unavailable.

### Fixed
- **Entity setVersion Methods (2025-12-02)**:
  - Added setVersion(Long) to AppointmentEntity, ContactEntity, ProjectEntity for test determinism
  - Removed duplicate setVersion method from AppointmentEntity that was causing compilation errors
  - Entity tests now pass with version field properly settable for testing purposes
  - Fixed ProjectService.java checkstyle error (line 93 exceeded 120 chars)
- **Test User Reset Guards (2025-12-02)**:
  - Hardened `TestUserSetup` to clear stale SecurityContext entries and recreate the test user if its row was deleted
  - Prevents FK/unique constraint collisions in legacy service singleton tests (e.g., `legacy10`, `legacy-apt`, `777` paths)
- **Singleton Test Isolation (2025-12-02)**:
  - Contact/Project singleton-sharing tests now reset the static service instance and reseed users before each run to avoid FK collisions (`legacy-100`, `legacy1` IDs) in CI

- **CodeQL Workflow Fix (2025-12-02)**:
  - Upgraded CodeQL action from v3 to v4 (v3 deprecated December 2026)
  - Replaced failing autobuild with explicit `mvn -B -DskipTests compile` (autobuild failed with JAVA_TOOL_OPTIONS conflict)

- **Pre-existing Config Test Fixes (2025-12-02)**:
  - Fixed RateLimitingFilterTest.doFilterInternal_logsSanitizedClientIp: Updated assertion to verify CR/LF is stripped rather than expecting `[unsafe-value]` (the implementation correctly sanitizes by removing CR/LF characters)
  - Fixed RequestLoggingFilterTest: Removed broken test for getSafeLogValue method which was refactored out in a previous change
  - Fixed ProjectService.java checkstyle error: Shortened SuppressFBWarnings justification to stay within 120-character line limit
- **ID Composite Key Coverage (2025-12-02)**:
  - Added ProjectContactId tests to cover equals/hashCode, setters, and Serializable contract for the composite key used by project-contact links
  - Incremented total test count to **1066** tests (up from 1056)

- **Project/Task Tracker Evolution Phases 1-5 Complete (ADR-0045) (2025-12-01)**:
  - **Phase 1 - Project Entity**: Project domain with CRUD API at `/api/v1/projects`, status tracking (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED), migration V8
  - **Phase 2 - Task Status/Due Date**: Enhanced Task with status (TODO/IN_PROGRESS/DONE), dueDate, createdAt/updatedAt timestamps, migration V9
  - **Phase 3 - Task-Project Linking**: Added projectId FK to Task for organization, migration V10, query parameters `?projectId={id}` and `?projectId=none`
  - **Phase 4 - Appointment Linking**: Added taskId and projectId FKs to Appointment for calendar context, migration V11, query parameters `?taskId={id}` and `?projectId={id}`
  - **Phase 5 - Task Assignment**: Added assigneeId FK to Task for collaboration, migration V12, query parameters `?assigneeId={userId}`, access control for task visibility
  - **Phase 6 - Contact-Project Linking**: Deferred to future implementation (V13 junction table planned but not implemented)
  - Full test coverage across all implemented phases (domain, persistence, service, API layers)
  - Backward compatibility maintained with sensible defaults and nullable FKs

### Fixed
- **Timestamp Bug Fix (2025-12-01)**: Fixed createdAt timestamp changing on update
  - Added Task constructor accepting timestamps for reconstitution from persistence
  - Updated TaskMapper.toDomain() to pass entity timestamps to domain
  - Added timestamp setters to TaskEntity for testing
  - Total tests now at **951**.
- **Legacy Singleton Tests Stabilized (2025-12-02)**: Annotated the contact/task/appointment service suites (legacy + Spring-booted + singleton bridge) with JUnit's `@Isolated` so Surefire's class-level parallelism no longer shares the same static `getInstance()` store across threads. This eliminates the flaky `DuplicateResourceException` bursts seen when multiple suites added the same legacy IDs concurrently.

### Added
- **E2E Tests for Phase 2 Task Fields (2025-12-01)**: Two new end-to-end tests
  - e2e_statusAndDueDate_persistThroughFullStack
  - e2e_updatedAtChangesAfterModification
- **Test Hardening for Rate Limiting/Auth/Controllers (2025-12-02)**:
  - Added `AuthControllerUnitTest`, `ContactControllerUnitTest`, and `ProjectControllerUnitTest` so controller-level guards and cookie parsing helpers can’t be mutated away.
  - Expanded `RateLimitingFilterTest`/`RequestLoggingFilterTest` with log-sanitization, bucket reset, and duration-math assertions; entity mapper tests now cover project/task linkage on appointments.
  - `RateLimitingFilter` sanitizes the login/register IP before using it as a cache key, and `ProjectController` reintroduces the legacy two-arg `getAll` overload for the admin guard tests.
  - Added `ProjectServiceLegacyTest` + repository guard tests to exercise the singleton migration path and `ensureRepositoriesAvailable()` guard; TaskService gained a deterministic overdue test via the clock override.
  - Total tests now at **1,026** with the same mutation/coverage gates (94%+ kills, 96%+ store coverage).

### Added
- **Project Entity and Project/Task Tracker Evolution (ADR-0045)**:
  - New `Project` domain entity with projectId (1-10 chars), name (1-50 chars), description (0-100 chars), and ProjectStatus enum (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED)
  - ProjectController REST API at `/api/v1/projects` with full CRUD operations (POST, GET, GET/{id}, PUT/{id}, DELETE/{id}) plus status filtering
  - ProjectService with per-user data isolation, getProjectsByStatus, and ADMIN-only getAllProjectsAllUsers
  - ProjectEntity JPA entity with @Version for optimistic locking, user_id FK, and created_at/updated_at timestamps
  - ProjectRepository Spring Data JPA repository with findByUserIdAndProjectId and findByUserIdAndStatus queries
  - ProjectMapper bidirectional mapper following ADR-0024 persistence patterns
  - JpaProjectStore and InMemoryProjectStore for Spring DI and legacy compatibility
  - Flyway migration V8__create_projects_table.sql with UNIQUE constraint on (project_id, user_id), status check constraint, and indexed columns
  - Comprehensive test coverage: ProjectTest (domain validation), ProjectStatusTest (enum), ProjectEntityTest (JPA), ProjectRepositoryTest (slice), ProjectMapperTest (mapper), JpaProjectStoreTest (store), ProjectServiceTest (service), ProjectControllerTest (REST API) - all following established testing patterns
  - Validation constants added to Validation.java: MAX_PROJECT_NAME_LENGTH (50), MAX_PROJECT_DESCRIPTION_LENGTH (100)
  - ProjectRequest/ProjectResponse DTOs with Bean Validation mirroring domain rules
  - Future phases planned in ADR-0045: Task status/due dates (V9), task-project linking (V10), appointment linking (V11), task assignment (V12), contact-project linking (V13)

- **Phase 7 UX Polish Complete**:
  - Search/pagination/sorting: Debounced SearchInput, Pagination component, SortableTableHead, useTableState hook with URL sync
  - Toast notifications: Sonner integration with useToast hook (success/error/info/warning)
  - Empty states: EmptyState component with icons and action buttons on all CRUD pages
  - Admin dashboard: RequireAdmin guard, AdminDashboard page with metrics/users/audit, role-based sidebar link
  - Accessibility (WCAG 2.1 AA): SkipLink, ARIA landmarks, keyboard navigation, focus management, form labels, live regions

- **Phase 6 Packaging + CI Complete**:
  - Created comprehensive `Makefile` with 30+ targets for local development:
    - Build targets: `build`, `test`, `test-unit`, `test-integration`, `verify`, `clean`
    - Docker targets: `docker-build`, `docker-up`, `docker-down`, `docker-logs`, `docker-clean`
    - Dev targets: `dev-db`, `run`, `run-dev`, `dev-setup`
    - Quality targets: `lint`, `coverage`, `mutation`, `security`, `spotbugs`
    - UI targets: `ui-install`, `ui-build`, `ui-dev`, `ui-test`
    - Database targets: `db-migrate`, `db-info`, `db-validate`
    - Help system: `make help` with color-coded categories
  - Extended `.github/workflows/java-ci.yml` with new `docker-build` job:
    - Runs after `build-test` succeeds
    - Builds Docker image using multi-stage Dockerfile with layer caching
    - Pushes to GitHub Container Registry (ghcr.io) on main/master
    - Health check: Starts docker-compose, waits for `/actuator/health`
    - Smoke test: Validates health endpoint returns `"status":"UP"`
    - Cleanup: Tears down stack with `docker-compose down -v`
    - Uploads docker build logs as artifact on failure
  - Code quality audit confirmed: 0 critical issues, 31 commendations
  - Documentation audit confirmed: All code already well-documented
- **Mutation-killing regression tests**:
  - Added `TaskServiceFallbackTest` to assert the in-memory legacy store is still invoked inside `addTask`, preventing PIT from replacing the fallback with a no-op.
  - Extended `RateLimitingFilterTest` with log-capturing + boundary tests for the new sanitizers so log injection defenses can’t be removed without failing tests.
  - Extended `RequestLoggingFilterTest` with direct tests for `getSafeLogValue`, `getSafeUserAgent`, and blank-query filtering to keep the inline sanitizers mutation-proof.

### Security
- Added `.env`, `.env.local`, `.env.*.local` to `.gitignore` to prevent accidental secret commits

- **Log Injection Prevention (Inline Validation)**:
  - Refactored `RateLimitingFilter` to use inline validation methods (`logSafeValue`, `logRateLimitExceeded`, `getSafeLogValue`) that validate input before logging.
  - Refactored `RequestLoggingFilter` to use inline validation methods (`getSafeLogValue`, `getSafeUserAgent`) that strip CR/LF and control characters.
  - Pattern validation ensures only safe characters (`^[A-Za-z0-9 .:@/_-]+$`) reach log statements.
  - Invalid inputs return safe placeholders (`[null]`, `[empty]`, `[unsafe-value]`).
  - This approach allows CodeQL to trace data flow and verify sanitization.

- **Store and Mapper Test Coverage Improvements**:
  - Added comprehensive null parameter validation tests to `JpaContactStoreTest`, `JpaTaskStoreTest`, and `JpaAppointmentStoreTest`.
  - Added save/update path tests covering both insert (new entity) and update (existing entity) branches.
  - Added `insert()`, `findAll()`, and `deleteAll()` method tests for complete JPA store coverage.
  - Added `updateEntity` null check tests to `ContactMapperTest`, `TaskMapperTest`, and `AppointmentMapperTest`.
  - Store coverage improved from 78% to **96%** (instructions), 73% to **97%** (branches).
  - All JPA stores now at **100%** coverage.
  - Mapper coverage improved to **95%+** with ContactMapper and TaskMapper at 100%.
  - Total tests now at **949**.

- **Phase 5.5 DAST + Runtime Security** (complete):
  - Added password strength validation to `RegisterRequest` via `@Pattern` regex (uppercase, lowercase, digit required).
  - Added Content-Security-Policy (CSP) header to `SecurityConfig` (default-src 'self', script-src 'self', etc.).
  - Created `docs/architecture/threat-model.md` with STRIDE analysis, CORS/JWT/rate-limiting security model, and known mitigations.
  - Added `.github/workflows/zap-scan.yml` for OWASP ZAP baseline and API scanning in CI.
  - Created `.zap/rules.tsv` for ZAP rule configuration.
  - Added 4 new auth controller tests for password strength validation (no uppercase, no lowercase, no digit).
  - Added CSP header integration test in `SecurityConfigIntegrationTest`.

- **README System Requirements**: Added a toolchain/hardware table plus OS guidance, and documented Node.js 22+ to match the frontend-maven-plugin + CI workflow.
- **ADR-0040**: Request Tracing and Logging Infrastructure – detailed documentation for CorrelationIdFilter, RequestLoggingFilter, and RequestUtils with test coverage.
- **ADR-0041**: PII Masking in Log Output – phone number and address masking patterns with Logback configuration.
- **ADR-0042**: Docker Containerization Strategy – multi-stage build, layer caching, JVM configuration, and compose stack.
- **README Observability section**: New comprehensive section documenting all Phase 5 filters with detailed scenario coverage for 40+ test methods across config tests.
- **JPA Store unit tests**: Added `JpaContactStoreTest`, `JpaTaskStoreTest`, `JpaAppointmentStoreTest` verifying repository delegations and UnsupportedOperationException guards for deprecated legacy methods.
- **Entity ID getter tests**: Added reflection-based tests for `getId()` in all entity classes to kill PIT EmptyObjectReturnValsMutator survivors.
- **User.isEnabled() disabled state test**: Added test case for `enabled=false` to kill BooleanTrueReturnValsMutator.
- **CorrelationIdFilter boundary tests**: Added exact boundary tests for MAX_CORRELATION_ID_LENGTH (64 chars) to catch ConditionalsBoundaryMutator.
- **RequestLoggingFilter query string tests**: Added tests verifying query string inclusion/exclusion in logs.

### Fixed
- RateLimitingFilter now routes all client IPs, usernames, and paths through the shared `sanitizeForLogging` helper so CodeQL log-injection alerts are eliminated.
- RequestLoggingFilter now defines `MAX_USER_AGENT_LENGTH`, eliminating the undefined constant compile error while still trimming verbose headers.
- README REST API docs now explain the optimistic locking workflow and list the 401/403 responses that `GlobalExceptionHandler`/Spring Security emit so callers know why those codes appear.
- ZAP workflows now resolve the built JAR explicitly and fail fast if multiple or zero artifacts exist before launching the scan harness.
- Added an explicit `@SuppressWarnings` annotation and JavaDoc note in `GlobalExceptionHandler#handleOptimisticLock` so we keep returning generic 409 responses without leaking entity metadata.
- AuthController now issues/clears JWT cookies via `ResponseCookie` + `HttpHeaders.SET_COOKIE` so SameSite attributes are preserved without clobbering other Set-Cookie headers during login/logout.
- Frontend `tokenStorage.getUser()` now guards `JSON.parse` with try/catch, purging corrupt sessionStorage entries instead of crashing on malformed data (aligns with SPA security notes).
- CODEBASE_AUDIT duplicate heading removed and the Phase 5 checklist in `docs/REQUIREMENTS.md` now reflects the completed security/observability scope described elsewhere.
- Removed the `final` modifier from `contactapp.security.User` so Hibernate can generate lazy-load proxies for the `@ManyToOne(fetch = LAZY)` relations in Contact/Task/Appointment entities.
- Added `X-XSRF-TOKEN` to `Access-Control-Allow-Headers` so CSRF-protected mutating requests succeed when the SPA runs on `localhost:5173`.
- Optimistic locking failures are now translated to HTTP 409 responses via `GlobalExceptionHandler` instead of bubbling up as 500 errors, giving clients a user-friendly “refresh and retry” message.
- Configures `spring.flyway.placeholders.system_user_password_hash` (env override `SYSTEM_USER_PASSWORD_HASH`) so the V5 migration runs outside tests without Flyway placeholder failures.
- SecurityConfig now allows `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` while keeping `/actuator/metrics/**` and `/actuator/prometheus` authenticated per ADR-0039 and `ActuatorEndpointsTest`.
- Prometheus actuator endpoint now works in tests with `@AutoConfigureObservability`
- H2 identity sequence conflict resolved in V5 migration
- Rate limit maps bounded with Caffeine to prevent DoS
- JWT secret no longer has default value (required in production)
- MDC.remove() used instead of MDC.clear() for proper cleanup
- SPA deep links and browser refreshes now load without a pre-existing JWT by permitting non `/api/**` GET requests in `SecurityConfig`; `/login` can be visited directly.
- Frontend no longer wipes tokens on legitimate 403 responses—only 401 Unauthorized forces logout so accidental forbidden calls do not drop the session.
- Postgres V6 migration now initializes surrogate key sequences with a minimum of 1 (using `setval(..., false)` when the table is empty) to avoid `value 0 is out of bounds` errors during Testcontainers spins.
- Reworked `contactapp.security.User` constructor to normalize/validate inputs before assigning fields and documented the intentional exceptions in a SpotBugs exclude filter so the `CT_CONSTRUCTOR_THROW` rule no longer fails builds.
- Dev and test profiles now override `app.auth.cookie.secure` to `false`, so local/Test profile runs can still issue HttpOnly JWT cookies over HTTP while production keeps Secure cookies enforced.

### Added
- `micrometer-registry-prometheus-simpleclient` for Micrometer 1.13+ compatibility
- Test utilities: `@WithMockAppUser`, `TestUserSetup`, `TestUserFactory`
- `@AutoConfigureObservability` annotation on ActuatorEndpointsTest
- Regression tests to kill remaining PIT survivors: legacy `add*` duplicate coverage in `ContactServiceLegacyTest`/`TaskServiceLegacyTest`/`AppointmentServiceLegacyTest`, repository-backed `existsById` false-path asserts in `Jpa*StoreTest`, RequestLoggingFilter sanitization/masking tests, RateLimitingFilter wait-time + cache reset tests, and a fresh-token guard in `JwtServiceTest`.

### Changed
- Dependabot Maven job now runs weekly on Mondays at 15:30 ET (America/New_York) to keep updates predictable without daily noise.
- Maven now defaults `skipITs=true` so local `mvn verify` no longer requires Docker; Ubuntu CI and any Docker-enabled runs pass `-DskipITs=false`, Windows runners stay on `-DskipITs=true`, and README/ADR-0004/agents/requirements now document the opt-in behavior.
- README/INDEX/agents now describe the HttpOnly cookie auth flow and sessionStorage-based profile caching so doc guidance matches the secure SPA implementation.
- V5 migration uses MERGE INTO and resets identity sequence to avoid conflicts

### Security
- Tightened the Content-Security-Policy to remove `'unsafe-inline'` from `style-src`; the React app ships external stylesheets only, and all docs/meta tags now mirror the stricter header.
- `CookieCsrfTokenRepository` now customizes the SPA-visible `XSRF-TOKEN` cookie with `SameSite=Lax` plus the `server.servlet.session.cookie.secure` flag so OWASP ZAP rule 10054 no longer fails missing-attribute checks.
- Hardened the HttpOnly cookie rollout: documented credentialed CORS headers + migration plan
  (ADR-0043, CI/CD plan), shortened JWT TTL to 30 minutes with a dedicated `app.auth.cookie.secure`
  property, required secure cookies in the prod profile, updated the threat model with PII masking,
  rate-limit lockouts, admin override auditing, and operational controls, and moved the ZAP workflow
  plus startup CLI secrets into GitHub Secrets with a failing health-check guard.
- **JWT cookie CSRF enforcement**: Re-enabled CSRF protection for `/api/v1/**`, added `/api/auth/csrf-token` plus SPA-side double-submit headers, and updated integration tests to include CSRF tokens now that JWTs live in HttpOnly cookies.
- **Log injection fixes**: Sanitized HTTP method/URI/query logging plus rate-limit key/path logging to strip CR/LF characters before hitting Logback, and tightened integration tests to use tenant-scoped repository lookups while documenting intentional deprecated API coverage in the legacy store/mapper tests.
- **react-router vulnerability**: Bumped `react-router` and `react-router-dom` from 7.0.2 to 7.9.6 to fix CVE for pre-render data spoofing.
- **Per-user data isolation**: Added `user_id` column to contacts, tasks, and appointments tables (V5 migration). Services now filter all queries by authenticated user. ADMIN users access multi-tenant exports via a POST `/api/v1/admin/query` override (JSON body flag + `X-Admin-Override` header + CSRF token) with the legacy `?all=true` query parameter scheduled for removal after **2026-02-01**.
- **Rate limiting**: Implemented bucket4j token-bucket rate limiting to protect against brute force and DoS attacks (login combines 5 requests/minute per username + 100/minute per IP with 15-minute lockouts after repeated failures):
  - `/api/auth/login`: 5 requests/minute per username (plus 100/minute per IP)
  - `/api/auth/register`: 3 requests/minute per IP
  - `/api/v1/**`: 100 requests/minute per authenticated user
- **PII masking in logs**: Phone numbers masked as `***-***-1234`, addresses show only city/state (e.g., `123 Main St, Portland, OR 97214` → `Portland, OR`).
- **SPA login + sanitized logging**: React router now exposes `/login`, `RequireAuth`, and `PublicOnlyRoute` components so the SPA authenticates before calling `/api/v1/**`, with `AuthController` issued JWTs persisted via secure-cookie guidance. `RequestLoggingFilter` was rewritten to mask client IPs, redact token/password query parameters, include normalized user-agent strings, and leverage the shared `RequestUtils` helper for safe header parsing.
- **Flyway secret placeholders**: H2/Postgres `V5__add_user_id_columns.sql` migrations now accept `${system_user_password_hash}` placeholders (no plaintext hashes in git) and wrap ALTER/CREATE statements in conditional guards so reruns remain idempotent. The migration directory split (`common`, `h2`, `postgresql`) mirrors production vs. test behaviors.

### Added
- **Phase 5 Observability Stack (ADR-0039)**:
  - Structured JSON logging in production via logstash-logback-encoder.
  - Correlation ID tracking (X-Correlation-ID header) via `CorrelationIdFilter`.
  - Request/response audit logging via `RequestLoggingFilter` (configurable).
  - Prometheus metrics endpoint at `/actuator/prometheus`.
  - Kubernetes-style liveness/readiness probes at `/actuator/health/liveness` and `/actuator/health/readiness`.
- **Docker packaging**:
  - Multi-stage `Dockerfile` with Eclipse Temurin 17 JRE, layered JAR extraction, non-root user.
  - Production `docker-compose.yml` with postgres, app, and optional pgadmin services.
  - Health checks, resource limits, and environment variable configuration.
  - `.env.example` template and `.dockerignore` for secure builds.
- **Rate limit configuration**: `RateLimitConfig` class with YAML binding for tunable limits.

### Fixed
- **User security hardening**: Added Validation `validateEmail`, enforced email format + BCrypt hash requirements in `User`, and expanded unit tests.
- **Users table auditing**: Added JPA lifecycle callbacks (@PrePersist/@PreUpdate) in `V4__create_users_table.sql` so `updated_at` reflects the latest modification automatically.
- **JWT secret handling**: `JwtService` now Base64-decodes the configured secret (with UTF-8 fallback) so docs and implementation align.
- **Dev profile Postgres driver**: `application.yml` now sets the PostgreSQL driver (and the launcher injects `SPRING_DATASOURCE_DRIVER_CLASS_NAME`) so running with `SPRING_PROFILES_ACTIVE=dev` or `python scripts/dev_stack.py --database postgres` no longer fails with "Driver org.h2.Driver claims to not accept jdbcUrl".
- **Docker compose CLI detection**: `scripts/dev_stack.py --database postgres` now auto-detects whether `docker compose` or `docker-compose` is available, preventing "unknown shorthand flag: 'f' in -f" errors on older Docker installs.
- **SpotBugs false positive**: Added `@SuppressFBWarnings` to `ApplicationContextProvider.setApplicationContext()` for standard Spring ApplicationContextAware pattern.
- **Race condition in add methods**: Fixed check-then-insert race condition in `ContactService.addContact()`, `TaskService.addTask()`, and `AppointmentService.addAppointment()` by removing the `existsById()` pre-check entirely and treating the database's UNIQUE constraints as the single source of truth. Violations now raise `DuplicateResourceException`, which the REST layer maps to HTTP 409 via the global exception handler.
- **UI API endpoint mismatch**: Changed API base from `/api` to `/api/v1` to match backend controller mappings.
- **Appointment date field name**: Fixed `date` → `appointmentDate` to match backend `AppointmentRequest` DTO.
- **Missing ID fields in forms**: Added ID input field to Contact, Task, and Appointment create forms (backend requires ID in request body).
- **Vite proxy configuration**: Updated proxy to forward `/api/v1`, `/actuator`, `/swagger-ui`, and `/v3/api-docs` to backend.

### Added
- **ADR-0036**: Documented the plan for an admin-only dashboard using role-based routing in the existing SPA; Phase 7 checklist updated with the new deliverable.
- **Settings page** (`SettingsPage.tsx`): Profile management (name, email with localStorage persistence), appearance settings (dark mode toggle, 5 color themes), and data management (clear settings).
- **Help page** (`HelpPage.tsx`): Getting started guide, feature documentation, developer resources (Swagger UI, OpenAPI spec, health check links), keyboard shortcuts.
- **Profile hook** (`useProfile.ts`): Manages user profile (name, email, initials) in localStorage with auto-computed initials.
- **TopBar navigation**: Avatar dropdown now links to Settings and Help pages, shows user initials from profile.
- **README Development URLs table**: Clear distinction between `:5173` (React UI) and `:8080` (API/Swagger).
- **Security test coverage**: Added mutation-focused tests for service `add*` persistence failures plus new `JwtServiceTest`, `JwtAuthenticationFilterTest`, `CustomUserDetailsServiceTest`, and the config/logging/rate-limit suites, bringing the repo to **571 tests** (577 with ITs) with **95% PIT coverage** (594/626 mutants killed) and **96% line coverage** on mutated classes.

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
- **Phase 4 Frontend Tests (complete)**:
  - Added Vitest + React Testing Library with 22 component/unit tests: `schemas.test.ts` (10 tests), `ContactForm.test.tsx` (6 tests), `ContactsPage.test.tsx` (6 tests).
  - Added Playwright E2E tests with 5 happy-path tests covering Contacts CRUD: list, create, edit, delete, validation errors.
  - Created `vitest.config.ts`, `playwright.config.ts`, `src/test/setup.ts`, and `src/test/test-utils.tsx` for test infrastructure.
  - Added npm scripts: `npm run test:run` (Vitest), `npm run test:e2e` (Playwright), `npm run test:coverage`.
  - Updated `.gitignore` with `test-results/`, `playwright-report/`, `coverage/` directories.
- Mapper null-guard tests, fresh JPA entity accessor suites, and additional `findById` Optional-empty assertions in the legacy `InMemory*Store` tests so persistence and fallback layers cover both branches, pushing the suite well past **400 tests** with **99% line coverage** on mutated classes.
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
- `scripts/dev_stack.py` now supports `--database postgres`, Docker Compose startup, datasource/profile defaults, and multi-argument Maven goals; README/PROJECT_SUMMARY explain the new workflow.
- `ApplicationTest.mainMethodCoverage` now launches `Application` with `--server.port=0` so PIT/CI runs pick an ephemeral port instead of colliding with another JVM on the agent.
- README/REQUIREMENTS/agents/Roadmap now document the expanded **571-test** suite (577 with ITs) and PIT's **95% mutation (594/626) / 96% line coverage** after the new regression tests.
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
  - Added 75 controller tests (21 Contact + 35 Task + 19 Appointment) covering:
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
