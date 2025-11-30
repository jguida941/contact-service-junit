# CS320 Milestone 1 - Contact Service
[![Java CI](https://img.shields.io/github/actions/workflow/status/jguida941/contact-service-junit/java-ci.yml?branch=master&label=Java%20CI&style=for-the-badge&logo=githubactions&logoColor=white&color=16A34A)](https://github.com/jguida941/contact-service-junit/actions/workflows/java-ci.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/jguida941/contact-service-junit/codeql.yml?branch=master&label=CodeQL&style=for-the-badge&logo=github&logoColor=white&color=16A34A)](https://github.com/jguida941/contact-service-junit/actions/workflows/codeql.yml)
[![Codecov](https://img.shields.io/codecov/c/github/jguida941/contact-service-junit/master?label=Codecov&style=for-the-badge&logo=codecov&logoColor=white&color=CA8A04)](https://codecov.io/gh/jguida941/contact-service-junit)
[![JaCoCo](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jguida941/contact-service-junit/master/badges/jacoco.json&style=for-the-badge)](#qa-summary)
[![PITest](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jguida941/contact-service-junit/master/badges/mutation.json&style=for-the-badge)](#qa-summary)
[![SpotBugs](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jguida941/contact-service-junit/master/badges/spotbugs.json&style=for-the-badge)](#static-analysis--quality-gates)
[![OWASP Dependency-Check](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jguida941/contact-service-junit/master/badges/dependency.json&style=for-the-badge)](#static-analysis--quality-gates)
[![License](https://img.shields.io/badge/License-MIT-1D4ED8?style=for-the-badge)](LICENSE)

Small Java project for the CS320 Contact Service milestone, now expanded to Task and Appointment. The work breaks down into four pieces:
1. Build the `Contact` and `ContactService` classes exactly as described in the requirements.
2. Prove every rule with unit tests (length limits, null checks, unique IDs, and add/update/delete behavior) using the shared `Validation` helper so exceptions surface clear messages.
3. Mirror the same patterns for the `Task` entity/service (ID/name/description) so both domains share validation, atomic updates, and singleton storage.
4. Apply the same validation/service patterns for `Appointment` (ID/date/description) with date-not-past enforcement and defensive date copies.

Everything is packaged under `contactapp` with layered sub-packages (`domain`, `service`, `api`, `persistence`); production classes live in `src/main/java` and the JUnit tests in `src/test/java`. Spring Boot 3.4.12 provides the runtime with actuator health/info endpoints.

## Table of Contents
- [Getting Started](#getting-started)
- [Folder Highlights](#folder-highlights)
- [Design Decisions & Highlights](#design-decisions--highlights)
- [Architecture Overview](#architecture-overview)
- [Validation & Error Handling](#validation--error-handling)
- [Testing Strategy](#testing-strategy)
- [Spring Boot Infrastructure](#applicationjava--spring-boot-infrastructure)
- [Static Analysis & Quality Gates](#static-analysis--quality-gates)
- [Backlog](#backlog)
- [CI/CD Pipeline](#cicd-pipeline)
- [QA Summary](#qa-summary)
- [Self-Hosted Mutation Runner Setup](#self-hosted-mutation-runner-setup)

## Getting Started
1. Install Java 17 and Apache Maven (3.9+).
2. Run `mvn verify` from the project root to compile everything, execute the JUnit suite, and run Checkstyle/SpotBugs/JaCoCo quality gates.
3. Start the application with `mvn spring-boot:run`:
   - Health/info actuator endpoints at `http://localhost:8080/actuator/health`
   - **Swagger UI** at `http://localhost:8080/swagger-ui.html`
   - **OpenAPI spec** at `http://localhost:8080/v3/api-docs`
   - REST APIs at `/api/v1/contacts`, `/api/v1/tasks`, `/api/v1/appointments`
4. Open the folder in IntelliJ/VS Code if you want IDE assistance—the Maven project model is auto-detected.
5. Planning note: Phases 0-2.5 complete (Spring Boot scaffold, REST API + DTOs, API fuzzing) with 278 tests (100% mutation score). The roadmap for persistence, UI, and security lives in `docs/REQUIREMENTS.md`. ADR-0014..0022 capture the selected stack and implementation decisions.

## Folder Highlights
| Path                                                                                                                 | Description                                                                                     |
|----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| [`src/main/java/contactapp/Application.java`](src/main/java/contactapp/Application.java)                             | Spring Boot entrypoint (`@SpringBootApplication`).                                              |
| [`src/main/java/contactapp/domain/Contact.java`](src/main/java/contactapp/domain/Contact.java)                       | Contact entity enforcing the ID/name/phone/address constraints.                                 |
| [`src/main/java/contactapp/domain/Task.java`](src/main/java/contactapp/domain/Task.java)                             | Task entity (ID/name/description) mirroring the requirements document.                          |
| [`src/main/java/contactapp/domain/Appointment.java`](src/main/java/contactapp/domain/Appointment.java)               | Appointment entity (ID/date/description) with date-not-past enforcement.                        |
| [`src/main/java/contactapp/domain/Validation.java`](src/main/java/contactapp/domain/Validation.java)                 | Centralized validation helpers (not blank, length, numeric, date-not-past checks).              |
| [`src/main/java/contactapp/service/ContactService.java`](src/main/java/contactapp/service/ContactService.java)       | @Service bean with in-memory CRUD, uniqueness checks, and validation reuse.                     |
| [`src/main/java/contactapp/service/TaskService.java`](src/main/java/contactapp/service/TaskService.java)             | Task service API (add/delete/update) mirroring the `ContactService` patterns.                   |
| [`src/main/java/contactapp/service/AppointmentService.java`](src/main/java/contactapp/service/AppointmentService.java) | Appointment service with in-memory CRUD and ID trim/validation guards.                        |
| [`src/main/resources/application.yml`](src/main/resources/application.yml)                                           | Profile-based configuration (dev/test/prod) with actuator lockdown.                             |
| [`src/test/java/contactapp/ApplicationTest.java`](src/test/java/contactapp/ApplicationTest.java)                     | Spring Boot context load smoke test.                                                            |
| [`src/test/java/contactapp/ActuatorEndpointsTest.java`](src/test/java/contactapp/ActuatorEndpointsTest.java)         | Verifies actuator endpoint security (health/info exposed, others blocked).                      |
| [`src/test/java/contactapp/ServiceBeanTest.java`](src/test/java/contactapp/ServiceBeanTest.java)                     | Verifies service beans are injectable and singletons.                                           |
| [`src/test/java/contactapp/domain/ContactTest.java`](src/test/java/contactapp/domain/ContactTest.java)               | Unit tests for the `Contact` class (valid + invalid scenarios).                                 |
| [`src/test/java/contactapp/domain/TaskTest.java`](src/test/java/contactapp/domain/TaskTest.java)                     | Unit tests for the `Task` class (trimming, invalid inputs, and atomic update validation).       |
| [`src/test/java/contactapp/domain/AppointmentTest.java`](src/test/java/contactapp/domain/AppointmentTest.java)       | Unit tests for Appointment entity (ID/date/description validation).                             |
| [`src/test/java/contactapp/domain/ValidationTest.java`](src/test/java/contactapp/domain/ValidationTest.java)         | Boundary/blank/null/future coverage for the shared validation helpers.                          |
| [`src/test/java/contactapp/service/ContactServiceTest.java`](src/test/java/contactapp/service/ContactServiceTest.java) | Unit tests for ContactService (singleton behavior and CRUD).                                  |
| [`src/test/java/contactapp/service/TaskServiceTest.java`](src/test/java/contactapp/service/TaskServiceTest.java)     | Unit tests for `TaskService` (singleton behavior and CRUD).                                     |
| [`src/test/java/contactapp/service/AppointmentServiceTest.java`](src/test/java/contactapp/service/AppointmentServiceTest.java) | Unit tests for AppointmentService singleton and CRUD behavior.                            |
| [`src/main/java/contactapp/api/ContactController.java`](src/main/java/contactapp/api/ContactController.java)         | REST controller for Contact CRUD operations at `/api/v1/contacts`.                              |
| [`src/main/java/contactapp/api/TaskController.java`](src/main/java/contactapp/api/TaskController.java)               | REST controller for Task CRUD operations at `/api/v1/tasks`.                                    |
| [`src/main/java/contactapp/api/AppointmentController.java`](src/main/java/contactapp/api/AppointmentController.java) | REST controller for Appointment CRUD operations at `/api/v1/appointments`.                      |
| [`src/main/java/contactapp/api/GlobalExceptionHandler.java`](src/main/java/contactapp/api/GlobalExceptionHandler.java) | Maps exceptions to HTTP responses (400, 404, 409).                                            |
| [`src/main/java/contactapp/api/CustomErrorController.java`](src/main/java/contactapp/api/CustomErrorController.java) | Ensures ALL errors return JSON (including Tomcat-level errors).                               |
| [`src/main/java/contactapp/config/JsonErrorReportValve.java`](src/main/java/contactapp/config/JsonErrorReportValve.java) | Tomcat valve ensuring container-level errors return JSON (see ADR-0022).                    |
| [`src/main/java/contactapp/config/TomcatConfig.java`](src/main/java/contactapp/config/TomcatConfig.java)             | Registers JsonErrorReportValve with embedded Tomcat.                                            |
| [`src/main/java/contactapp/api/dto/`](src/main/java/contactapp/api/dto/)                                             | Request/Response DTOs with Bean Validation (`ContactRequest`, `TaskRequest`, etc.).             |
| [`src/main/java/contactapp/api/exception/`](src/main/java/contactapp/api/exception/)                                 | Custom exceptions (`ResourceNotFoundException`, `DuplicateResourceException`).                  |
| [`src/test/java/contactapp/ContactControllerTest.java`](src/test/java/contactapp/ContactControllerTest.java)         | MockMvc integration tests for Contact API (30 tests).                                           |
| [`src/test/java/contactapp/TaskControllerTest.java`](src/test/java/contactapp/TaskControllerTest.java)               | MockMvc integration tests for Task API (21 tests).                                              |
| [`src/test/java/contactapp/AppointmentControllerTest.java`](src/test/java/contactapp/AppointmentControllerTest.java) | MockMvc integration tests for Appointment API (20 tests).                                       |
| [`src/test/java/contactapp/GlobalExceptionHandlerTest.java`](src/test/java/contactapp/GlobalExceptionHandlerTest.java) | Unit tests for GlobalExceptionHandler methods (4 tests).                                      |
| [`src/test/java/contactapp/CustomErrorControllerTest.java`](src/test/java/contactapp/CustomErrorControllerTest.java) | Unit tests for CustomErrorController (17 tests).                                              |
| [`docs/requirements/contact-requirements/`](docs/requirements/contact-requirements/)                                 | Contact assignment requirements and checklist.                                                  |
| [`docs/requirements/appointment-requirements/`](docs/requirements/appointment-requirements/)                         | Appointment assignment requirements and checklist.                                              |
| [`docs/requirements/task-requirements/`](docs/requirements/task-requirements/)                                       | Task assignment requirements and checklist (same format as Contact).                            |
| [`docs/architecture/2025-11-19-task-entity-and-service.md`](docs/architecture/2025-11-19-task-entity-and-service.md) | Task entity/service design plan with Definition of Done and phased approach.                    |
| [`docs/architecture/2025-11-24-appointment-entity-and-service.md`](docs/architecture/2025-11-24-appointment-entity-and-service.md) | Appointment entity/service implementation record.                                               |
| [`docs/adrs/README.md`](docs/adrs/README.md)                                                                         | Architecture Decision Record index (ADR-0001…ADR-0022).                                         |
| [`docs/ci-cd/`](docs/ci-cd/)                                                                                         | CI/CD design notes (pipeline plan + badge automation).                                          |
| [`docs/design-notes/`](docs/design-notes/)                                                                           | Informal design notes hub; individual write-ups live under `docs/design-notes/notes/`.          |
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)                                                                       | **Master document**: scope, architecture, phased plan, checklist, and code examples.            |
| [`docs/ROADMAP.md`](docs/ROADMAP.md)                                                                                 | Quick phase overview (points to REQUIREMENTS.md for details).                                   |
| [`docs/INDEX.md`](docs/INDEX.md)                                                                                     | Full file/folder navigation for the repository.                                                 |
| [`agents.md`](agents.md)                                                                                             | AI assistant entry point with constraints and stack decisions.                                  |
| [`pom.xml`](pom.xml)                                                                                                 | Maven build file (dependencies, plugins, compiler config).                                      |
| [`config/checkstyle`](config/checkstyle)                                                                             | Checkstyle configuration used by Maven/CI quality gates.                                        |
| [`config/owasp-suppressions.xml`](config/owasp-suppressions.xml)                                                     | Placeholder suppression list for OWASP Dependency-Check.                                        |
| [`scripts/ci_metrics_summary.py`](scripts/ci_metrics_summary.py)                                                     | Helper that parses JaCoCo/PITest/Dependency-Check reports and posts the QA summary table in CI. |
| [`scripts/serve_quality_dashboard.py`](scripts/serve_quality_dashboard.py)                                           | Tiny HTTP server that opens `target/site/qa-dashboard` locally after downloading CI artifacts.  |
| [`scripts/api_fuzzing.py`](scripts/api_fuzzing.py)                                                                   | API fuzzing helper for local Schemathesis runs (starts app, fuzzes, exports OpenAPI spec).      |
| [`.github/workflows`](.github/workflows)                                                                             | CI/CD pipelines (tests, quality gates, release packaging, CodeQL, API fuzzing).                 |

## Design Decisions & Highlights
- **Immutable identifiers** - `contactId` is set once in the constructor and never mutates, which keeps map keys stable and mirrors real-world record identifiers.
- **Centralized validation** - Every constructor/setter call funnels through `Validation.validateNotBlank`, `validateLength`, `validateDigits`, and (for appointments) `validateDateNotPast`, so IDs, names, phones, addresses, and dates all share one enforcement pipeline.
- **Fail-fast IllegalArgumentException** - Invalid input is a caller bug, so we throw standard JDK exceptions with precise messages and assert on them in tests.
- **ConcurrentHashMap storage strategy** - Milestone 1 uses in-memory `ConcurrentHashMap` stores (inside the singleton `ContactService`, `TaskService`, and `AppointmentService`) for predictable average O(1) CRUD plus thread-safe access, while still treating each service class as the seam for future persistence layers.
- **Defensive copies** - Each entity provides a `copy()` method, and `getDatabase()` returns defensive copies so external callers cannot mutate internal state; safe to surface over APIs.
- **Boolean service API** - Every service's `add/delete/update` methods return `boolean` so callers know immediately whether the operation succeeded (`true`) or why it failed (`false` for duplicate IDs, missing IDs, etc.). That keeps the milestone interfaces lightweight while still letting JUnit assertions check the outcome without extra exception types.
- **Security posture** - Input validation in the domain + service layers acts as the first defense layer; nothing reaches the in-memory store unless it passes the guards.
- **Testing depth** - Parameterized JUnit 5 tests, AssertJ assertions, JaCoCo coverage, and PITest mutation scores combine to prove the validation logic rather than just executing it.

## Architecture Overview
### Domain Layer (`Contact`)
- Minimal state (ID, first/last name, phone, address) with the `contactId` locked after construction.
- Constructor delegates to setters so validation logic fires in one place.
- Address validation uses the same length helper as IDs/names, ensuring the 30-character maximum cannot drift.

### Validation Layer (`Validation.java`)
- `validateNotBlank(input, label)` - rejects null, empty, and whitespace-only fields with label-specific messages.
- `validateLength(input, label, min, max)` - enforces 1-10 char IDs/names and 1-30 char addresses (bounds are parameters, so future changes touch one file).
- `validateDigits(input, label, requiredLength)` - requires digits-only phone numbers with exact length (10 in this project).
- `validateDateNotPast(date, label)` - rejects null dates and any timestamp strictly before now (dates equal to "now" are accepted to avoid millisecond-boundary flakiness), powering the Appointment rules. **Note:** The default uses `Clock.systemUTC()`, so "now" is evaluated in UTC; callers in significantly different timezones should construct dates with UTC awareness. An overload accepting a `Clock` parameter enables deterministic testing of boundary conditions.
- These helpers double as both correctness logic and security filtering.

### Service Layer (`ContactService`)
- Uses a static `ConcurrentHashMap<String, Contact>` keyed by `contactId`, ensuring Spring DI and `getInstance()` share the same data.
- Provides add/update/delete orchestration, validates/normalizes IDs before touching the map, and delegates all field rules to `Contact` (constructor + `update(...)`) and `Validation`.
- The service layer remains the seam for swapping in persistence or caching later without touching the entity/tests.

### Storage & Extension Points
**ConcurrentHashMap<String, Contact> (current backing store)**
| Operation | Average | Worst | Space |
|-----------|---------|-------|-------|
| add/get   | O(1)    | O(n)  | O(1)  |
| update    | O(1)    | O(n)  | O(1)  |
| delete    | O(1)    | O(n)  | O(1)  |
- This strategy meets the course requirements while documenting the upgrade path (DAO, repository pattern, etc.).

  <br>

## [Contact.java](src/main/java/contactapp/domain/Contact.java) / [ContactTest.java](src/test/java/contactapp/domain/ContactTest.java)

### Service Snapshot
- `Contact` acts as the immutable ID holder with mutable first/last name, phone, and address fields.
- Constructor delegates to setters so validation stays centralized and consistent for both creation and updates.
- Validation trims IDs/names/addresses before storing them; phone numbers are stored as provided and must already be 10 digits (whitespace fails the digit check instead of being silently removed).
- `copy()` creates a defensive copy by validating the source state and reusing the public constructor, keeping defensive copies aligned with validation rules.

## Validation & Error Handling

### Validation Pipeline
```mermaid
graph TD
    A[input]
    C{Field type}
    D["validateLength(input): validateNotBlank + measure trimmed length"]
    E[trim & assign id/name/address]
    F["validateDigits(input): validateNotBlank + digits-only + exact length"]
    G[assign phone as provided]
    X[IllegalArgumentException]

    A --> C
    C -->|id/name/address| D
    C -->|phone| F
    D -->|pass| E
    D -->|fail| X
    F -->|pass| G
    F -->|fail| X
```
- Text fields (`contactId`, `firstName`, `lastName`, `address`): `validateLength` first calls `validateNotBlank` on the original input, then measures `input.trim().length()` against the bounds. If valid, the caller trims and stores.
- Phone numbers: `validateDigits` calls `validateNotBlank` on the original input, then checks for digits-only and exact length. No trimming; whitespace fails the digit check.
- Because the constructor routes through the setters, the exact same pipeline applies whether the object is being created or updated.

### Error Message Philosophy
```java
// Bad
throw new IllegalArgumentException("Invalid input");

// Good
throw new IllegalArgumentException("firstName length must be between 1 and 10");
```
- Specific, label-driven messages make debugging easier and double as documentation. Tests assert on the message text so regressions are caught immediately.

### Exception Strategy
| Exception Type | Use Case           | Recovery? | Our Choice |
|----------------|--------------------|-----------|------------|
| Checked        | Recoverable issues | Maybe     | ❌          |
| Unchecked      | Programming errors | Fix code  | ✅          | 

- We throw `IllegalArgumentException` (unchecked) because invalid input is a caller bug and should crash fast.

### Propagation Flow
```mermaid
graph TD
    A[Client request] --> B[ContactService]
    B --> C[Validation]
    C --> D{valid?}
    D --> E[IllegalArgumentException]
    E --> F[Client handles/fails fast]
    D --> G[State assignment]
```
- Fail-fast means invalid state never reaches persistence/logs, and callers/tests can react immediately.

## Testing Strategy

### Approach & TDD
- Each validator rule started as a failing test, then the implementation was written until the suite passed.
- `ContactTest` serves as the living specification covering both the success path and every invalid scenario.
- `TaskTest` and `TaskServiceTest` mirror the same workflow for the Task domain/service, reusing the shared `Validation` helper and singleton patterns, and include invalid update cases to prove atomicity.

### Parameterized Coverage
- `@ParameterizedTest` + `@CsvSource` enumerate the invalid IDs, names, phones, and addresses so we don’t duplicate boilerplate tests.
```java
@ParameterizedTest
@CsvSource({
    "'', 'contactId must not be null or blank'",
    "' ', 'contactId must not be null or blank'",
    "'12345678901', 'contactId length must be between 1 and 10'"
})
void testInvalidContactId(String id, String expectedMessage) {
    assertThatThrownBy(() -> new Contact(id, "first", "last", "1234567890", "123 Main St"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
}
```

### Assertion Patterns
- AssertJ’s `hasFieldOrPropertyWithValue` validates the happy path in one fluent statement.
- `assertThatThrownBy().isInstanceOf(...).hasMessage(...)` proves exactly which validation rule triggered.

### Scenario Coverage
- `testSuccessfulCreation` validates the positive constructor path (all fields stored).
- `testValidSetters` ensures setters update fields when inputs pass validation.
- `testConstructorTrimsStoredValues` confirms IDs, names, and addresses are normalized via `trim()`.
- `testFailedCreation` (`@ParameterizedTest`) enumerates every invalid ID/name/phone/address combination and asserts the corresponding message.
- `testFailedSetFirstName` (`@ParameterizedTest`) exercises the setter's invalid inputs (blank/long/null).
- `testUpdateRejectsInvalidValuesAtomically` (`@MethodSource`) proves invalid updates throw and leave the existing Contact state unchanged.
- `testCopyRejectsNullInternalState` (`@ParameterizedTest`) uses reflection to corrupt each internal field (contactId, firstName, lastName, phone, address), proving the `validateCopySource()` guard triggers for all null branches.
- `ValidationTest.validateLengthAcceptsBoundaryValues` proves 1/10-char names and 30-char addresses remain valid.
- `ValidationTest.validateLengthRejectsBlankStrings` and `ValidationTest.validateLengthRejectsNull` ensure blanks/nulls fail before length math is evaluated.
- `ValidationTest.validateLengthRejectsTooLong` hits the max-length branch to keep upper-bound validation covered.
- `ValidationTest.validateLengthRejectsTooShort` covers the min-length branch so both ends of the range are exercised.
- `ValidationTest.validateDigitsRejectsBlankStrings` and `ValidationTest.validateDigitsRejectsNull` ensure the phone validator raises the expected messages before regex/length checks.
- `ValidationTest.validateDigitsAcceptsValidPhoneNumber` proves valid 10-digit inputs pass without exception.
- `ValidationTest.validateDigitsRejectsNonDigitCharacters` asserts non-digit input triggers the "must only contain digits 0-9" message.
- `ValidationTest.validateDigitsRejectsWrongLength` asserts wrong-length input triggers the "must be exactly 10 digits" message.
- `ValidationTest.validateDateNotPastAcceptsFutureDate`, `validateDateNotPastRejectsNull`, and `validateDateNotPastRejectsPastDate` assert the appointment date guard enforces the non-null/not-in-the-past contract before any Appointment state mutates.
- `ValidationTest.validateDateNotPastAcceptsDateExactlyEqualToNow` (added for PITest) uses `Clock.fixed()` to deterministically test the exact boundary where `date.getTime() == clock.millis()`, killing the boundary mutant (`<` vs `<=`).
- `ValidationTest.privateConstructorIsNotAccessible` (added for line coverage) exercises the private constructor via reflection to cover the utility class pattern.
- Spring Boot tests use Mockito's subclass mock-maker (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) to avoid agent attach issues on newer JDKs while still enabling MockMvc/context testing.

> **Note (JDK 25+):** When running tests on JDK 25 or later, you may see a warning like `Mockito is currently self-attaching to enable the inline-mock-maker`. This is expected and harmless; Mockito's subclass mock-maker handles mocking without requiring the Java agent, so the warning does not affect test correctness.

<br>

## [ContactService.java](src/main/java/contactapp/service/ContactService.java) / [ContactServiceTest.java](src/test/java/contactapp/service/ContactServiceTest.java)

### Service Snapshot
- **Shared static store** - The backing `ConcurrentHashMap` is static, so both Spring DI and `getInstance()` share the same data regardless of initialization order.
- **Atomic uniqueness guard** - `addContact` rejects null inputs up front and calls `ConcurrentHashMap.putIfAbsent(...)` directly so duplicate IDs never overwrite state even under concurrent access.
- **Thread-safe updates** - `updateContact` uses `ConcurrentHashMap.computeIfPresent(...)` for atomic lookup + update, then delegates to `Contact.update(...)` guaranteeing the constructor's length/null/phone rules apply.
- **Shared validation** - `deleteContact` uses `Validation.validateNotBlank` for IDs; all paths reuse the same `Validation` helpers.
- **Service-level lookup methods** - `getAllContacts()` returns a list of defensive copies; `getContactById(id)` validates/trims the ID and returns `Optional<Contact>`. Controllers use these instead of `getDatabase()` for better encapsulation.
- **Defensive views** - `getDatabase()` returns an unmodifiable snapshot of defensive copies (via `Contact.copy()`) so callers can't mutate internal state; tests use `clearAllContacts()` (package-private) to reset between runs.

## Validation & Error Handling

### Validation Pipeline
```mermaid
graph TD
    A[Service call] --> B{Operation}
    B -->|add| C["contact != null?"]
    C -->|no| X[IllegalArgumentException]
    C -->|yes| D["contactId already validated by Contact"]
    D --> E["putIfAbsent(contactId, contact)"]
    B -->|delete| F["validateNotBlank(contactId)"]
    F --> G["trim id, remove from map"]
    G -->|missing| H[return false]
    G -->|found| J[entry removed]
    B -->|update| K["validateNotBlank(contactId)"]
    K --> L["trim id, computeIfPresent"]
    L -->|missing| H
    L -->|found| M["Contact.update(...) reuses Validation"]
    M --> N[updated contact]
```
- Delete/update paths validate and trim IDs before map access; add relies on the Contact constructor’s validation and stores the already-normalized `contactId`.
- IDs are trimmed before delete/update map access so callers with surrounding whitespace behave consistently.
- Setter delegation ensures errors surface with the same messages as the constructor (e.g., “phone must be exactly 10 digits”).

### Error Message Philosophy
- Null service inputs raise `IllegalArgumentException` with explicit labels (e.g., “contact must not be null”).
- ID validation errors come from the shared `Validation` helper so delete/update use the exact strings already asserted in `ContactTest`.
- Field-level issues rely on the `Contact` setters, so callers receive consistent messaging whether the data was supplied in the constructor or during an update.

### Propagation Flow
```mermaid
graph TD
    A[Client] --> B[ContactService]
    B --> C{Operation result}
    C -->|success| D[State updated]
    C -->|false| E[Not found / duplicate]
    C -->|exception| F[Validation message]
```
- Successful operations mutate the in-memory map; duplicate IDs or missing contacts simply return `false` so clients can branch without exceptions.
- Validation failures bubble up as unchecked exceptions, which keeps the fail-fast stance consistent with the domain model.
## Testing Strategy

### Approach & TDD
- The service initially shipped as a stub; once CRUD code landed, targeted JUnit tests captured the scenarios before refactoring anything else.
- `@BeforeEach` clears the singleton’s backing map so tests remain isolated even though the service is shared.

### Assertion Patterns
- AssertJ collections helpers (`containsKey`, `doesNotContainKey`) verify map entries; field values are checked separately since `getDatabase()` returns defensive copies.
- Field assertions after update reuse `hasFieldOrPropertyWithValue` so the tests read like a change log.
- Boolean outcomes are asserted explicitly (`isTrue()/isFalse()`) so duplicate and missing-ID branches stay verified.


### Scenario Coverage
- `testGetInstance` ensures the singleton accessor always returns a concrete service before any CRUD logic runs.
- `testGetInstanceReturnsSameReference` proves repeated invocations return the same singleton instance.
- `testAddContact` proves the happy path and that the map contains the stored entry with correct field values.
- `testAddDuplicateContactFails` confirms the boolean contract for duplicates and that the original data remains untouched.
- `testAddContactNullThrows` hits the defensive null guard so callers see a clear `IllegalArgumentException` instead of an NPE.
- `testDeleteContact` exercises removal plus assertion that the key disappears.
- `testDeleteMissingContactReturnsFalse` covers the branch where no contact exists for the given id.
- `testDeleteContactBlankIdThrows` shows ID validation runs even on deletes, surfacing the standard "contactId must not be null or blank" message.
- `testUpdateContact` verifies every mutable field changes via setter delegation.
- `testUpdateContactTrimsId` confirms IDs are trimmed before lookups during updates.
- `testUpdateContactBlankIdThrows` ensures update throws when the ID is blank so validation mirrors delete().
- `testUpdateMissingContactReturnsFalse` covers the "not found" branch so callers can rely on the boolean result.
- `testGetDatabaseReturnsDefensiveCopies` proves callers cannot mutate internal state through the returned snapshot.
- `testGetInstanceColdStart` uses reflection to reset the static instance, then verifies `getInstance()` creates a new instance when none exists—ensuring full branch coverage of the lazy initialization pattern.
- `testGetContactByIdReturnsContact` verifies getContactById returns the contact when it exists.
- `testGetContactByIdReturnsEmptyWhenNotFound` verifies getContactById returns empty when contact doesn't exist.
- `testGetContactByIdBlankIdThrows` verifies getContactById throws when ID is blank.
- `testGetContactByIdTrimsId` verifies getContactById trims the ID before lookup.
- `testGetContactByIdReturnsDefensiveCopy` verifies getContactById returns a defensive copy.
- `testGetAllContactsReturnsEmptyList` verifies getAllContacts returns empty list when no contacts exist.
- `testGetAllContactsReturnsAllContacts` verifies getAllContacts returns all contacts.

<br>

## [Task.java](src/main/java/contactapp/domain/Task.java) / [TaskTest.java](src/test/java/contactapp/domain/TaskTest.java)

### Service Snapshot
- Task IDs are required, trimmed, and immutable after construction (length 1-10).
- Name (≤20 chars) and description (≤50 chars) share one helper so constructor, setters, and `update(...)` all enforce identical rules.
- `Task#update` validates both values first, then swaps them in one shot; invalid inputs leave the object untouched.
- `copy()` creates a defensive copy by validating the source state and reusing the public constructor, keeping defensive copies aligned with validation rules.
- Tests mirror Contact coverage: constructor trimming, happy-path setters/update, and every invalid-path exception message.

## Validation & Error Handling

### Validation Pipeline
```mermaid
graph TD
    A[Constructor input] --> B["validateLength(taskId, 1-10)"]
    B -->|ok| C[trim & store taskId]
    B -->|fail| X[IllegalArgumentException]
    C --> D["validateLength(name, 1-20)"]
    D -->|ok| E[trim & store name]
    D -->|fail| X
    E --> F["validateLength(description, 1-50)"]
    F -->|ok| G[trim & store description]
    F -->|fail| X
```
- Constructor validates taskId, then delegates to setters for name/description.
- `validateLength` measures `input.trim().length()` on the original input, then the caller trims before storing.
- Setters (`setName`, `setDescription`) use the same `validateLength` + trim pattern for updates.
- `update(...)` validates both fields first, then assigns both atomically if they pass.

### Error Message Philosophy
- All strings come from `Validation.validateLength`, so failures always say `<label> must not be null or blank` or `<label> length must be between X and Y`.
- Tests assert the exact messages so a wording change immediately fails the suite.

### Propagation Flow
```mermaid
graph TD
    A[Client] --> B[Task constructor/setter/update]
    B --> C{Validation}
    C -->|pass| D[State updated]
    C -->|fail| E[IllegalArgumentException]
```
- No silent coercion; invalid data throws fast so tests/users fix the source input.

## Testing Strategy

### Approach & TDD
- Started with constructor trimming tests, then added invalid cases before writing setters/update so every branch had a failing test first.

### Assertion Patterns
- AssertJ `hasFieldOrPropertyWithValue` keeps success assertions short.
- `assertThatThrownBy(...).hasMessage(...)` locks in the Validation wording for each failure mode.

### Scenario Coverage
- Constructor stores trimmed values and rejects null/blank/too-long IDs, names, and descriptions.
- Setters accept valid updates and reject invalid ones with the same helper-generated messages.
- `update(...)` replaces both mutable fields atomically and never mutates on invalid input.
- `testUpdateRejectsInvalidValuesAtomically` (`@MethodSource`) enumerates invalid name/description pairs (blank/empty/null/over-length) and asserts the Task remains unchanged when validation fails.
- `testCopyRejectsNullInternalState` (`@ParameterizedTest`) uses reflection to corrupt each internal field (taskId, name, description), proving the `validateCopySource()` guard triggers for all null branches.
- `TaskServiceTest` mirrors the entity atomicity: invalid updates (blank name) throw and leave the stored task unchanged.

  <br>

## [TaskService.java](src/main/java/contactapp/service/TaskService.java) / [TaskServiceTest.java](src/test/java/contactapp/service/TaskServiceTest.java)

### Service Snapshot
- Uses a static `ConcurrentHashMap<String, Task>` so Spring DI and `getInstance()` share the same data; `clearAllTasks()` (package-private) resets state for tests.
- `addTask` rejects null tasks and uses `putIfAbsent` so uniqueness checks and inserts are atomic.
- `deleteTask` validates + trims ids before removal; `updateTask` uses `ConcurrentHashMap.computeIfPresent(...)` for thread-safe atomic lookup + update.
- **Service-level lookup methods** - `getAllTasks()` returns a list of defensive copies; `getTaskById(id)` validates/trims the ID and returns `Optional<Task>`. Controllers use these instead of `getDatabase()`.
- `getDatabase()` returns an unmodifiable snapshot of defensive copies (via `Task.copy()`) so callers can't mutate internal state.

## Validation & Error Handling

### Validation Pipeline
```mermaid
graph TD
    A[TaskService call] --> B{Operation}
    B -->|add| C["task != null?"]
    C -->|no| X[IllegalArgumentException]
    C -->|yes| D["taskId already validated by Task"]
    D --> E["putIfAbsent(taskId, task)"]
    B -->|delete| F["validateNotBlank(taskId)"]
    F --> G["trim id, remove from map"]
    G -->|missing| Y[return false]
    G -->|found| J[entry removed]
    B -->|update| K["validateNotBlank(taskId)"]
    K --> L["trim id, computeIfPresent"]
    L -->|missing| Y
    L -->|found| H["Task.update(newName, description)"]
```
- Delete/update paths validate and trim IDs before map access; add relies on the Task constructor’s validation and stores the normalized `taskId`.
- Updates delegate to `Task.update(...)`, keeping atomicity centralized; `putIfAbsent` returns `false` on duplicate IDs just like the Contact service.

### Error Message Philosophy
- Service-level guards emit just two strings (`"task must not be null"` and `"taskId must not be null or blank"`); everything else flows from the Task entity.

### Propagation Flow
```mermaid
graph TD
    A[Client] --> B[TaskService]
    B --> C{Outcome}
    C -->|true| D[State updated]
    C -->|false| E[Duplicate/missing]
    C -->|exception| F[Validation message]
```
- Boolean return mirrors ContactService: duplicates/missing IDs report `false`, invalid inputs throw.

## Testing Strategy

### Approach & TDD
- `@BeforeEach` clears the singleton to keep tests isolated.
- Tests were written alongside each service method so duplicates/missing/blank cases were covered before implementation settled.
- Invalid update inputs throw and leave the stored task unchanged, mirroring the Task entity’s atomicity guarantees.

### Assertion Patterns
- AssertJ checks (`containsKey`, `doesNotContainKey`, `isTrue/isFalse`) verify map entries; field values are checked separately since `getDatabase()` returns defensive copies.
- `assertThatThrownBy` verifies the null-task guard and blank-id validation messages.

### Scenario Coverage
- `testSingletonInstance` proves the singleton accessor returns the same instance.
- `testAddTask` stores a task and asserts map contents.
- `testAddDuplicateTaskIdFails` returns `false` on duplicate IDs and preserves the original entry.
- `testAddTaskNullThrows` asserts the null guard message.
- `testDeleteTask` removes an existing entry.
- `testDeleteTaskBlankIdThrows` and `testDeleteMissingTaskReturnsFalse` cover validation/missing delete branches.
- `testUpdateTask` changes name/description; `testUpdateTaskTrimsId` shows whitespace IDs are trimmed.
- `testUpdateTaskBlankIdThrows` and `testUpdateMissingTaskReturnsFalse` cover validation/missing update branches.
- `testUpdateTaskInvalidValuesLeaveStateUnchanged` proves invalid updates throw and leave the stored task unchanged.
- `testClearAllTasksRemovesEntries` proves the reset hook empties the backing store.
- `testGetDatabaseReturnsDefensiveCopies` proves callers cannot mutate internal state through the returned snapshot.
- `testGetInstanceColdStart` uses reflection to reset the static instance, then verifies `getInstance()` creates a new instance when none exists—ensuring full branch coverage of the lazy initialization pattern.
- `testGetTaskByIdReturnsTask` verifies getTaskById returns the task when it exists.
- `testGetTaskByIdReturnsEmptyWhenNotFound` verifies getTaskById returns empty when task doesn't exist.
- `testGetTaskByIdBlankIdThrows` verifies getTaskById throws when ID is blank.
- `testGetTaskByIdTrimsId` verifies getTaskById trims the ID before lookup.
- `testGetAllTasksReturnsEmptyList` verifies getAllTasks returns empty list when no tasks exist.
- `testGetAllTasksReturnsAllTasks` verifies getAllTasks returns all tasks.

  <br>

## [Appointment.java](src/main/java/contactapp/domain/Appointment.java) / [AppointmentTest.java](src/test/java/contactapp/domain/AppointmentTest.java)

### Service Snapshot
- Appointment IDs are required, trimmed, and immutable after construction (length 1-10).
- `appointmentDate` uses `java.util.Date`, must not be null or in the past, is stored/returned via defensive copies, and is serialized/deserialized as ISO 8601 with millis + offset (`yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, UTC).
- Descriptions are required, trimmed, and capped at 50 characters; constructor and update share the same validation path.

### Validation & Error Handling

#### Validation Pipeline
```mermaid
flowchart TD
    X[IllegalArgumentException]

    C1[Constructor inputs] --> C2["validateLength(appointmentId, 1-10)"]
    C2 -->|ok| C3[trim & store id]
    C2 -->|fail| X
    C3 --> C4[validateDateNotPast]
    C4 -->|ok| C5[defensive copy of date stored]
    C4 -->|fail| X
    C5 --> C6["validateLength(description, 1-50)"]
    C6 -->|ok| C7[trim & store description]
    C6 -->|fail| X
    U1[update newDate, newDescription] --> U2[validateDateNotPast]
    U2 -->|fail| X
    U2 --> U3["validateLength(description, 1-50)"]
    U3 -->|fail| X
    U3 --> U4[copy date + trim & store description]
```
- Constructor: `validateLength` validates/measures trimmed ID, then trim & store. Then delegates to setters for date + description.
- `validateLength` measures `input.trim().length()` on the original input, then the caller trims before storing (matches Contact/Task pattern).
- Dates are validated via `Validation.validateDateNotPast` and copied on set/get to prevent external mutation.
- `update(...)` validates both inputs before mutating, keeping updates atomic.

### Testing Strategy
- `AppointmentTest` covers trimmed creation with defensive date copies, description setter happy path, invalid constructor cases (null/blank/over-length id/description, null/past dates), invalid description setters, invalid updates (null/past dates, bad descriptions) that leave state unchanged, and defensive getters.
- AssertJ getters/field checks verify stored values; future/past dates are relative to “now” to avoid flakiness.

### Scenario Coverage
- `testSuccessfulCreationTrimsAndCopiesDate` validates trim/defensive copy on construction.
- `testUpdateReplacesValuesAtomically` confirms date/description updates and defensive date copy.
- `testSetDescriptionAcceptsValidValue` covers setter happy path.
- `testGetAppointmentDateReturnsDefensiveCopy` ensures callers can't mutate stored dates.
- `testConstructorValidation` enumerates invalid id/description/null/past date cases.
- `testSetDescriptionValidation` covers invalid description setter inputs.
- `testUpdateRejectsInvalidValuesAtomically` enumerates invalid update inputs and asserts state remains unchanged.
- `testCopyProducesIndependentInstance` verifies copy() produces an independent instance with identical values.
- `testCopyRejectsNullInternalState` (`@ParameterizedTest`) uses reflection to corrupt each internal field (appointmentId, appointmentDate, description), proving the `validateCopySource()` guard triggers for all null branches.

## [AppointmentService.java](src/main/java/contactapp/service/AppointmentService.java) / [AppointmentServiceTest.java](src/test/java/contactapp/service/AppointmentServiceTest.java)

### Service Snapshot
- **Shared static store** - The backing `ConcurrentHashMap<String, Appointment>` is static, so both Spring DI and `getInstance()` share the same data.
- **Atomic uniqueness guard** - `addAppointment` rejects null inputs, validates IDs (already trimmed by the `Appointment` constructor), and uses `putIfAbsent` so duplicate IDs never overwrite existing entries.
- **Shared validation** - `deleteAppointment` trims/validates IDs; `updateAppointment` trims IDs and delegates field rules to `Appointment.update(...)` via `computeIfPresent` to avoid a get-then-mutate race.
- **Service-level lookup methods** - `getAllAppointments()` returns a list of defensive copies; `getAppointmentById(id)` validates/trims the ID and returns `Optional<Appointment>`. Controllers use these instead of `getDatabase()`.
- **Defensive views** - `getDatabase()` returns an unmodifiable snapshot of defensive copies (via `Appointment.copy()`, which validates the source and reuses the public constructor); `clearAllAppointments()` (package-private) resets state between tests.

### Validation & Error Handling

#### Validation Pipeline
```mermaid
graph TD
    A[Service call] --> B{Operation}
    B -->|addAppointment| C["appointment != null?"]
    C -->|no| X[IllegalArgumentException]
    C -->|yes| D["validateNotBlank(appointmentId)"]
    D --> E["putIfAbsent(id, appointment)"]

    B -->|deleteAppointment| F["validateNotBlank + trim(appointmentId)"]
    F --> G["remove(trimmedId)"]

    B -->|updateAppointment| H["validateNotBlank + trim(appointmentId)"]
    H --> I["computeIfPresent(trimmedId, appointment.update(date, desc))"]
    I -->|missing| J[return false]
    I -->|found| K["Appointment.update(...) reuses Validation"]
```
- Add path validates the (already trimmed) ID and uses `putIfAbsent`; delete/update trim + validate IDs before map access; validation failures bubble as `IllegalArgumentException`; duplicates/missing entries return `false`.

### Testing Strategy
- `AppointmentServiceTest` mirrors the Contact/Task patterns: singleton identity, add success/duplicate/null add, add-blank-id guard, delete success/blank/missing, update success/blank/missing/trimmed IDs, clear-all, and defensive-copy verification.
- Future dates are generated relative to “now” to keep “not in the past” checks stable.

### Scenario Coverage
- `testSingletonInstance` proves the singleton accessor returns the same instance.
- `testAddAppointment` stores a future-dated appointment and asserts map contents.
- `testAddDuplicateAppointmentIdFails` returns `false` on duplicate IDs and preserves the original entry.
- `testAddAppointmentNullThrows` asserts the null guard message.
- `testAddAppointmentWithBlankIdThrows` hits the add-path ID validation guard.
- `testDeleteAppointment` removes an existing entry.
- `testDeleteAppointmentBlankIdThrows` and `testDeleteMissingAppointmentReturnsFalse` cover validation/missing delete branches.
- `testUpdateAppointment` changes date/description; `testUpdateAppointmentTrimsId` shows whitespace IDs are trimmed.
- `testGetDatabaseReturnsDefensiveCopies` proves callers cannot mutate internal state through snapshots.
- `testUpdateAppointmentBlankIdThrows` and `testUpdateMissingAppointmentReturnsFalse` cover validation/missing update branches.
- `testClearAllAppointmentsRemovesEntries` proves the reset hook empties the backing store.
- `testGetInstanceColdStart` uses reflection to reset the static instance, then verifies `getInstance()` creates a new instance when none exists—ensuring full branch coverage of the lazy initialization pattern.

> **Note:** `getAllAppointments()` and `getAppointmentById()` are tested implicitly via controller integration tests but lack dedicated unit tests in AppointmentServiceTest.

<br>

## REST API Layer (Phase 2)

### API Snapshot
- **Three REST controllers** expose CRUD endpoints under `/api/v1/{contacts,tasks,appointments}`.
- **Service-level lookup methods** - Controllers use `getAllXxx()` and `getXxxById(id)` service methods for better encapsulation instead of accessing `getDatabase()` directly.
- **DTOs with Bean Validation** (`ContactRequest`, `TaskRequest`, `AppointmentRequest`) validate input at the HTTP boundary before reaching domain logic.
- **Global exception handling** via `@RestControllerAdvice` maps exceptions to consistent JSON error responses.
- **Custom error controller** ensures ALL errors return JSON, including container-level errors (malformed requests, invalid paths) that bypass Spring MVC exception handling.
- **OpenAPI/Swagger UI** available at `/swagger-ui.html` and `/v3/api-docs` (powered by springdoc-openapi).
- **Enhanced OpenAPI spec**: Controllers use `@Tag`, `@Operation`, and `@ApiResponses` annotations to produce a production-quality spec with `application/json` content types and documented error responses (400/404/409).

### Endpoint Summary
| Resource     | Create (POST)          | Read (GET)                                          | Update (PUT)                | Delete (DELETE)             |
|--------------|------------------------|-----------------------------------------------------|-----------------------------|-----------------------------|
| Contacts     | `/api/v1/contacts`     | `/api/v1/contacts`, `/api/v1/contacts/{id}`         | `/api/v1/contacts/{id}`     | `/api/v1/contacts/{id}`     |
| Tasks        | `/api/v1/tasks`        | `/api/v1/tasks`, `/api/v1/tasks/{id}`               | `/api/v1/tasks/{id}`        | `/api/v1/tasks/{id}`        |
| Appointments | `/api/v1/appointments` | `/api/v1/appointments`, `/api/v1/appointments/{id}` | `/api/v1/appointments/{id}` | `/api/v1/appointments/{id}` |

### HTTP Status Codes
| Status | Meaning                       | When Used                                    |
|--------|-------------------------------|----------------------------------------------|
| 200    | OK                            | GET by ID, PUT update success                |
| 201    | Created                       | POST create success                          |
| 204    | No Content                    | DELETE success                               |
| 400    | Bad Request                   | Validation failure, malformed JSON           |
| 404    | Not Found                     | Resource with given ID does not exist        |
| 409    | Conflict                      | Duplicate ID on create                       |

### Validation Strategy (Two Layers)
```mermaid
flowchart TD
    A[HTTP Request] --> B[Bean Validation on DTO]
    B -->|fail| C[400 Bad Request]
    B -->|pass| D[Domain Constructor/Update]
    D -->|fail| C
    D -->|pass| E[Service Layer]
    E --> F[200/201/204 Response]
```
- **Bean Validation** (`@NotBlank`, `@Size`, `@Pattern`, `@FutureOrPresent`) catches invalid input early with user-friendly error messages.
- **Domain validation** (`Validation.validateLength`, `validateDigits`, `validateDateNotPast`) acts as a backup layer—same rules, same constants.
- DTO constraints use static imports from `Validation.MAX_*` constants to stay in sync with domain rules.

### Controller Tests (MockMvc)
- **ContactControllerTest** (30 tests): Happy path CRUD, validation errors, boundary tests, 404/409 scenarios.
- **TaskControllerTest** (21 tests): Same patterns adapted for Task entity.
- **AppointmentControllerTest** (20 tests): Date validation, past-date rejection, ISO 8601 format handling.
- **GlobalExceptionHandlerTest** (4 tests): Direct unit tests for exception handler methods (`handleIllegalArgument`, `handleNotFound`, `handleDuplicate`).
- **CustomErrorControllerTest** (17 tests): Unit tests for container-level error handling (status codes, JSON content type, message mapping).

### Test Isolation Pattern
Controller tests use reflection to access package-private `clearAll*()` methods on the autowired service:
```java
@BeforeEach
void setUp() throws Exception {
    final Method clearMethod = ContactService.class.getDeclaredMethod("clearAllContacts");
    clearMethod.setAccessible(true);
    clearMethod.invoke(contactService);  // Use autowired service, not getInstance()
}
```

<br>

## [Application.java](src/main/java/contactapp/Application.java) / Spring Boot Infrastructure

### Application Snapshot
- **Spring Boot 3.4.12** provides the runtime foundation with embedded Tomcat, auto-configuration, and actuator endpoints.
- `@SpringBootApplication` combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan` to wire everything together.
- Component scanning discovers `@Service` beans in `contactapp.service` package automatically.
- Services retain their singleton `getInstance()` pattern for backward compatibility while also supporting Spring DI via `@Autowired`.

### Package Structure
```
contactapp/
├── Application.java              # Spring Boot entrypoint
├── domain/                       # Domain entities (Contact, Task, Appointment, Validation)
├── service/                      # @Service beans (ContactService, TaskService, AppointmentService)
├── api/                          # REST controllers + DTOs + error handling
│   ├── ContactController.java    # Contact CRUD endpoints
│   ├── TaskController.java       # Task CRUD endpoints
│   ├── AppointmentController.java # Appointment CRUD endpoints
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice error mapping
│   ├── CustomErrorController.java # JSON error responses for container-level errors
│   ├── dto/                      # Request/Response DTOs with Bean Validation
│   └── exception/                # ResourceNotFoundException, DuplicateResourceException
├── config/                       # Tomcat/Spring configuration
│   ├── JsonErrorReportValve.java # Tomcat valve for JSON error responses (ADR-0022)
│   └── TomcatConfig.java         # Registers JsonErrorReportValve with embedded Tomcat
└── persistence/                  # Repository interfaces (Phase 3 - empty)
```

### Configuration
- **Profile-based settings** in `application.yml`:
  - `dev`: Debug logging, health details always shown
  - `test`: Minimal logging for fast CI runs
  - `prod`: Restricted health details, warn-level logging
- **Actuator lockdown**: Only `/actuator/health` and `/actuator/info` are exposed; all other endpoints (env, beans, metrics) return 404 per OWASP guidelines.

## [application.yml](src/main/resources/application.yml)

### Configuration Highlights
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info    # Only health and info exposed
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true           # Kubernetes liveness/readiness support
```

### Why These Defaults?
- **Security**: Actuator endpoints can expose sensitive information (environment variables, bean definitions, metrics). Locking them down by default follows defense-in-depth.
- **Observability**: Health and info endpoints are essential for orchestrators (Kubernetes probes, load balancer health checks) and operational dashboards.
- **Profiles**: Environment-specific behavior without code changes; `spring.profiles.active=dev` unlocks more verbose settings locally.

## [ApplicationTest.java](src/test/java/contactapp/ApplicationTest.java)

### Test Snapshot
- Smoke test verifying the Spring application context loads without errors.
- Empty test body is intentional: `@SpringBootTest` triggers context loading before any test runs. If wiring fails, the test fails with detailed error messages.

### Scenario Coverage
- `contextLoads` - Verifies the application context loads without exceptions.
- `mainMethodCoverage` - Calls `Application.main()` directly to ensure the entrypoint is exercised for JaCoCo line coverage.

### Why This Test Matters
- Catches configuration errors early: missing beans, circular dependencies, invalid property bindings, component scanning failures.
- Fast feedback loop: fails in seconds rather than waiting for full integration tests to discover wiring issues.

## [ActuatorEndpointsTest.java](src/test/java/contactapp/ActuatorEndpointsTest.java)

### Test Snapshot
- Integration tests verifying actuator endpoint security configuration using MockMvc.
- Confirms security posture matches `application.yml` settings.

### Scenario Coverage
- `healthEndpointReturnsUp` - Verifies `/actuator/health` returns 200 OK with `{"status":"UP"}`. Critical for Kubernetes probes and load balancer health checks.
- `infoEndpointReturnsOk` - Verifies `/actuator/info` returns 200 OK. Provides build metadata for operational dashboards.
- `envEndpointIsNotExposed` - Verifies `/actuator/env` returns 404. Prevents exposure of environment variables that may contain secrets.
- `beansEndpointIsNotExposed` - Verifies `/actuator/beans` returns 404. Prevents exposure of internal architecture details.
- `metricsEndpointIsNotExposed` - Verifies `/actuator/metrics` returns 404. Prevents exposure of JVM/application metrics.

### Security Rationale
| Endpoint            | Status    | Reason                           |
|---------------------|-----------|----------------------------------|
| `/actuator/health`  | ✅ Exposed | Required for orchestrator probes |
| `/actuator/info`    | ✅ Exposed | Build metadata for ops           |
| `/actuator/env`     | ❌ Blocked | May contain secrets              |
| `/actuator/beans`   | ❌ Blocked | Reveals architecture             |
| `/actuator/metrics` | ❌ Blocked | Aids attackers                   |

## [ServiceBeanTest.java](src/test/java/contactapp/ServiceBeanTest.java)

### Test Snapshot
- Integration tests verifying service beans are properly registered and injectable.
- Proves component scanning discovers all `@Service` classes.

### Scenario Coverage
- `contactServiceBeanExists` - Verifies `ContactService` is injectable via `@Autowired` and retrievable from `ApplicationContext`.
- `taskServiceBeanExists` - Verifies `TaskService` is injectable and present in context.
- `appointmentServiceBeanExists` - Verifies `AppointmentService` is injectable and present in context.
- `serviceBeansAreSingletons` - Verifies all three services are singletons (same instance across injection points), matching the expected behavior for stateful in-memory services.

### Why These Tests Matter
- Catches `NoSuchBeanDefinitionException` errors before they surface in controller tests (Phase 2).
- Documents the expected wiring behavior: services should be singletons.
- Verifies backward compatibility: Spring-managed beans should behave identically to `getInstance()` pattern.

<br>

## Static Analysis & Quality Gates

| Layer               | Tool                       | Focus                                                                                |
|---------------------|----------------------------|--------------------------------------------------------------------------------------|
| Coverage            | **JaCoCo**                 | Line/branch coverage enforcement during `mvn verify`.                                |
| Mutation            | **PITest**                 | Ensures assertions catch injected faults; threshold currently 70%.                   |
| Style & complexity  | **Checkstyle**             | Formatting, naming, indentation, import ordering, and boolean simplification.        |
| Bug patterns        | **SpotBugs**               | Bytecode bug-pattern scanning via `spotbugs-maven-plugin` (fails build on findings). |
| Dependency security | **OWASP Dependency-Check** | CVE scanning backed by `NVD_API_KEY` with optional skip fallback.                    |
| Semantic security   | **CodeQL**                 | Detects SQLi/XSS/path-traversal patterns in a separate workflow.                     |

Each layer runs automatically in CI, so local `mvn verify` mirrors the hosted pipelines.
- Dependabot runs daily against the Maven ecosystem and automatically opens PRs for available dependency upgrades.

## Mutation Testing & Quality Gates
- PITest runs inside `mvn verify`, so the new service tests contribute directly to the enforced mutation score.
- The GitHub Actions matrix uses the same suite, ensuring duplicate/add/delete/update scenarios stay green across OS/JDK combinations.
- GitHub Actions still executes `{ubuntu-latest, windows-latest} × {Java 17, Java 21}` with `MAVEN_OPTS="--enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true"`, so mutation coverage is enforced everywhere.
- The optional self-hosted lane remains available for long mutation sessions or extra capacity; see the dedicated section below.

## Testing Pyramid
```mermaid
graph TD
    A[Static analysis] --> B[Unit tests]
    B --> C[Service tests]
    C --> D[Integration tests]
    D --> E[Mutation tests]
```

## Checkstyle Rule Set
| Check Group                                                                                                          | Focus                                                                              |
|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `ImportOrder`, `AvoidStarImport`, `RedundantImport`                                                                  | Enforce ordered/separated imports, no wildcards, and no duplicates.                |
| `NeedBraces`, `LeftCurly`, `RightCurly`, `EmptyBlock`                                                                | Require braces and consistent brace placement; flag empty blocks.                  |
| `WhitespaceAround`, `WhitespaceAfter`, `NoWhitespaceBefore`, `NoWhitespaceAfter`, `SingleSpaceSeparator`, `ParenPad` | Enforce consistent spacing around tokens and parentheses.                          |
| `Indentation`, `LineLength`, `FileTabCharacter`, `NewlineAtEndOfFile`                                                | Align indentation, cap lines at 120 chars, disallow tabs, ensure trailing newline. |
| `ModifierOrder`, `RedundantModifier`                                                                                 | Keep modifier order canonical and drop redundant keywords.                         |
| `MethodLength`, `MethodParamPad`, `MethodName`, `ParameterName`, `LocalVariableName`, `MemberName`                   | Bound method size and enforce naming/padding conventions.                          |
| `HiddenField`                                                                                                        | Prevent locals/parameters from shadowing fields (except constructors/setters).     |
| `MagicNumber`                                                                                                        | Flags unwanted literals (excluding -1, 0, 1) to encourage constants.               |
| `SimplifyBooleanExpression`, `SimplifyBooleanReturn`, `OneStatementPerLine`                                          | Reduce complex boolean logic and keep one statement per line.                      |
| `FinalParameters`, `FinalLocalVariable`                                                                              | Encourage immutability for parameters and locals when possible.                    |


## SpotBugs
<img width="1037" height="767" alt="Screenshot 2025-11-18 at 4 16 06 PM" src="https://github.com/user-attachments/assets/419bd8e1-9974-4db2-ad3a-0f83a9c014db" />


## SpotBugs Commands
```bash
# Run SpotBugs as part of the normal build
mvn -Ddependency.check.skip=true verify

# Fail fast on SpotBugs findings during local iterations
mvn spotbugs:check

# Generate the HTML report at target/spotbugs.html
mvn spotbugs:spotbugs

# Open the SpotBugs GUI to inspect findings interactively
mvn spotbugs:gui
```
> CI already runs SpotBugs inside `mvn verify`; these commands help when iterating locally.

## Sonatype OSS Index (optional)
Dependency-Check also pings the Sonatype OSS Index service. When requests are anonymous the analyzer often rate-limits, which is why CI prints warnings like “An error occurred while analyzing … (Sonatype OSS Index Analyzer)”. To receive full results:
1. Create a free account at [ossindex.sonatype.org](https://ossindex.sonatype.org/) and generate an API token.
2. Add the credentials to your Maven `settings.xml`:
   ```xml
   <settings>
     <servers>
       <server>
         <id>ossindex</id>
         <username>YOUR_OSS_INDEX_USERNAME</username>
         <password>YOUR_OSS_INDEX_API_TOKEN</password>
       </server>
     </servers>
   </settings>
   ```
3. Run Maven with `-DossIndexServerId=ossindex` (or set the property permanently with `export MAVEN_OPTS="$MAVEN_OPTS -DossIndexServerId=ossindex"`). GitHub Actions can do the same by storing the username/token as repository secrets and writing the snippet above before `mvn verify`.

If you skip these steps, the OSS Index analyzer simply logs warnings while the rest of Dependency-Check continues to rely on the NVD feed.

## Backlog
- Full backlog lives in [`docs/logs/backlog.md`](docs/logs/backlog.md) so the README stays concise, and includes future ideas for reporting, observability, and domain enhancements.

## CI/CD Pipeline

### Jobs at a Glance
| Job                 | Trigger                                                                             | What it does                                                                                                                                                                                                 | Notes                                                                                                      |
|---------------------|-------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `build-test`        | Push/PR to main/master, release, manual dispatch                                    | Matrix `{ubuntu, windows} × {JDK 17, 21}` running `mvn verify` (tests + Checkstyle + SpotBugs + JaCoCo + PITest + Dependency-Check), builds QA dashboard, posts QA summary, uploads reports, Codecov upload. | Retries `mvn verify` with Dependency-Check/PITest skipped if the first attempt fails due to feed/timeouts. |
| `api-fuzz`          | Push/PR to main/master, manual dispatch                                             | Starts Spring Boot app, runs Schemathesis against `/v3/api-docs`, exports OpenAPI spec, publishes JUnit XML results. Fails on 5xx errors or schema violations.                                               | 20-minute timeout; exports spec as artifact for ZAP.                                                       |
| `container-test`    | Always (needs `build-test`)                                                         | Re-runs `mvn verify` inside `maven:3.9.9-eclipse-temurin-17` to prove a clean container build; retries with Dependency-Check/PITest skipped on failure.                                                      | Uses same MAVEN_OPTS for PIT attach.                                                                       |
| `mutation-test`     | Only when repo var `RUN_SELF_HOSTED == 'true'` and a `self-hosted` runner is online | Runs `mvn verify` on the self-hosted runner with PITest enabled; retries with Dependency-Check/PITest skipped on failure.                                                                                    | Optional lane; skipped otherwise.                                                                          |
| `release-artifacts` | Release event (`published`)                                                         | Packages the JAR and uploads it as an artifact; generates release notes.                                                                                                                                     | Not run on normal pushes/PRs.                                                                              |

### Local Command Cheat Sheet
| Command                                                                 | Purpose                                                                                  |
|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `mvn verify`                                                            | Full build: compile, unit tests, Checkstyle, SpotBugs, JaCoCo, PITest, Dependency-Check. |
| `mvn -Ddependency.check.skip=true -Dpit.skip=true verify`               | Fast local build when Dependency-Check feed is slow/unavailable.                         |
| `mvn spotbugs:check`                                                    | Run only SpotBugs and fail on findings.                                                  |
| `mvn -DossIndexServerId=ossindex verify`                                | Opt-in authenticated OSS Index for Dependency-Check (see Sonatype section).              |
| `cd ui/qa-dashboard && npm ci && npm run build`                         | Build the React QA dashboard locally (already built in CI).                              |
| `pip install schemathesis && python scripts/api_fuzzing.py --start-app` | Run API fuzzing locally (starts app, fuzzes, exports spec).                              |

### Matrix Verification
- `.github/workflows/java-ci.yml` runs `mvn -B verify` across `{ubuntu-latest, windows-latest} × {Java 17, Java 21}` to surface OS and JDK differences early.
- `container-test` reruns the same `mvn verify` flow inside `maven:3.9.9-eclipse-temurin-17` to prove the build works in a clean, reproducible environment; if quality gates fail, it retries with Dependency-Check and PITest skipped.

### Quality Gate Behavior
- Each matrix job executes the full suite (tests, JaCoCo, Checkstyle, SpotBugs, Dependency-Check, PITest).
- Checkstyle enforces formatting/import/indentation rules while SpotBugs scans bytecode for bug patterns and fails the build on findings.
- SpotBugs runs as part of every `mvn verify` run on the supported JDKs (currently 17 and 21 in CI) and fails the build on findings.
- Dependency-Check throttling is tuned via `nvdApiDelay` (defaults to 3500ms when an NVD API key is configured, 8000ms without a key) and honors `-Ddependency.check.skip=true` if the NVD feed is unreachable; PITest has a similar `-Dpit.skip=true` retry path so contributors stay unblocked but warnings remain visible.
- Python 3.12 is provisioned via `actions/setup-python@v5` so `scripts/ci_metrics_summary.py` runs consistently on both Ubuntu and Windows runners.
- Node.js 20 is provisioned via `actions/setup-node@v4` and the React dashboard under `ui/qa-dashboard/` is built every run so the artifacts contain the interactive QA console.
- Mutation coverage now relies on GitHub-hosted runners by default; the self-hosted lane is opt-in and only fires when the repository variable `RUN_SELF_HOSTED` is set.
- Dependabot checks run daily so Maven updates appear as automated PRs without waiting for the weekly window.
- After every matrix job, `scripts/ci_metrics_summary.py` posts a table to the GitHub Actions run summary showing tests, JaCoCo coverage, PITest mutation score, and Dependency-Check counts (with ASCII bars for quick scanning).
- The same summary script emits a dark-mode HTML dashboard (`target/site/qa-dashboard/index.html`) with quick stats and links to the JaCoCo, SpotBugs, Dependency-Check, and PITest HTML reports (packaged inside the `quality-reports-*` artifact for download) and drops `serve_quality_dashboard.py` next to the reports for easy local previewing.

### Caching Strategy
- Maven artifacts are cached via `actions/cache@v4` (`~/.m2/repository`) to keep builds fast.
- Dependency-Check data is intentionally purged every run (see the “Purge Dependency-Check database cache” step) to avoid stale or corrupted NVD downloads. If feed reliability improves we can re-enable caching in the workflow, but for now the clean slate proved more stable.

### Mutation Lane (Optional Self-Hosted Fallback)
- The standard matrix already executes PITest, but some contributors keep a self-hosted runner handy for long mutation sessions, experiments, or when GitHub-hosted capacity is saturated.
- Toggling the repository variable `RUN_SELF_HOSTED` to `true` enables the `mutation-test` job, which mirrors the hosted command line but runs on your own hardware with `MAVEN_OPTS="--enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true"`.

### Release Automation
- Successful workflows publish build artifacts, and the release workflow packages release notes so we can trace which commit delivered which binary.
- The `release-artifacts` job is intentionally gated with `if: github.event_name == 'release' && github.event.action == 'published'`, so you will see it marked as “skipped” on normal pushes or pull requests. It only runs when a GitHub release/tag is published.

### Coverage Publishing (Codecov)
- After JaCoCo generates `target/site/jacoco/jacoco.xml`, the workflow uploads it to [Codecov](https://codecov.io/gh/jguida941/contact-service-junit) so the coverage badge stays current.
- Setup steps (once per repository):
  1. Sign in to Codecov with GitHub and add this repo.
  2. Generate a repository token in Codecov and save it as the GitHub secret `CODECOV_TOKEN`.
  3. Re-run the workflow; each matrix job uploads coverage with a `flags` label (`os-jdk`).
- The badge at the top of this README pulls from the default `master` branch; adjust the URL if you maintain long-lived release branches.

### CodeQL Security Analysis
- `.github/workflows/codeql.yml` runs independently of the matrix job to keep static analysis focused.
- The workflow pins Temurin JDK 17 via `actions/setup-java@v4`, caches Maven dependencies, and enables the `+security-and-quality` query pack for broader coverage.
- GitHub's CodeQL `autobuild` runs the Maven build automatically; a commented `mvn` fallback is available if the repo ever needs a custom command.
- Concurrency guards prevent overlapping scans on the same ref, and `paths-ignore` ensures doc-only/image-only changes do not queue CodeQL unnecessarily.
- Triggers: pushes/PRs to `main` or `master` (respecting the filters), a weekly scheduled scan (`cron: 0 3 * * 0`), and optional manual dispatch.

### API Fuzzing (Schemathesis)
- `.github/workflows/api-fuzzing.yml` runs Schemathesis against the live OpenAPI spec to detect 5xx errors, schema violations, and edge cases.
- **Schemathesis v4+ compatibility**: The workflow uses updated options after v4 removed `--base-url`, `--hypothesis-*`, and `--junit-xml` flags.
- **Two-layer JSON error handling**: `JsonErrorReportValve` intercepts errors at the Tomcat container level, while `CustomErrorController` handles Spring-level errors. This ensures most error responses return `application/json`. Note: Extremely malformed URLs (invalid Unicode) fail at Tomcat's connector level before the valve, so `content_type_conformance` check is not used (see ADR-0022).
- **Workflow steps**:
  1. Build the JAR with `mvn -DskipTests package`.
  2. Start Spring Boot app in background, wait for `/actuator/health` to return `UP` (uses `jq` for robust JSON parsing).
  3. Export OpenAPI spec to `target/openapi/openapi.json` (artifact for ZAP/other tools).
  4. Run `schemathesis run` with `--checks not_a_server_error --checks response_schema_conformance --max-examples 50 --workers 1`.
  5. Stop app, publish summary to GitHub Actions.
- **Artifacts produced**:
  - `openapi-spec`: JSON/YAML OpenAPI specification for ZAP and other security tools.
  - `api-fuzzing-results`: Schemathesis output for debugging.
- **Local testing**: `pip install schemathesis && python scripts/api_fuzzing.py --start-app` runs the same fuzzing locally.
- **Failure criteria**: Any 5xx response, content-type mismatch, or response schema violation fails the workflow. Expected 400s from validation (e.g., past dates) are not flagged.

## CI/CD Flow Diagram
```mermaid
graph TD
    A[Push or PR or Release]
    B[Matrix verify Ubuntu+Windows JDK17+21]
    C{DepCheck or PITest fail?}
    D[Retry with skips]
    E[QA summary & artifacts & Codecov]
    F[Container verify Temurin17 Maven3.9.9]
    G{RUN_SELF_HOSTED set?}
    H[Self-hosted mutation lane]
    I[Release artifacts]
    J[API Fuzzing Schemathesis]
    K[OpenAPI spec artifact]

    A --> B --> E
    A --> J --> K
    B --> C
    C -->|yes| D --> B
    C -->|no| E
    B --> F --> G
    G -->|yes| H --> I
    G -->|no| I
```

## QA Summary
Each GitHub Actions matrix job writes a QA table (tests, coverage, mutation score, Dependency-Check status) to the run summary. The table now includes colored icons, ASCII bars, and severity breakdowns so drift stands out immediately. Open any workflow’s “Summary” tab and look for the “QA Metrics” section for the latest numbers.

## GitHub Actions QA Metrics Table

<img width="1271" height="970" alt="Screenshot 2025-11-20 at 4 41 19 AM" src="https://github.com/user-attachments/assets/69495c27-8e44-4383-8a41-192543384526" />

## QA Console React Dashboard

Need richer visuals?<br>
Download the `quality-reports-<os>-jdk<ver>` artifact from the workflow run, unzip it, and from the artifact root run:

```bash
cd ~/Downloads/quality-reports-<os>-jdk<ver>
python serve_quality_dashboard.py --path site
```

(If the artifact retains the `target/site` structure, change `--path site` to `--path target/site`.) <br>
Modern browsers block ES modules when loaded directly from `file://` URLs <br>
So the helper launches a tiny HTTP server, opens `http://localhost:<port>/qa-dashboard/index.html`, and serves the React dashboard with the correct `metrics.json`. <br> 
You’ll see the same KPIs, inline progress bars, and quick links over to the JaCoCo, SpotBugs, Dependency-Check, and PITest HTML reports, all sourced from the exact results of that build.

<img width="1029" height="769" alt="Screenshot 2025-11-19 at 10 07 25 PM" src="https://github.com/user-attachments/assets/d1fc1a3e-844d-4a7a-9e84-d78abcb248f3" />

<br>

## CodeCov Coverage Sunburst

<table>
  <tr>
    <td width="300" valign="middle">
      <a href="https://app.codecov.io/gh/jguida941/contact-service-junit">
        <img
          src="https://codecov.io/gh/jguida941/contact-service-junit/graphs/sunburst.svg?token=WUWITZ797X"
          alt="Codecov coverage sunburst"
        />
      </a>
    </td>
    <td valign="middle">
      The sunburst shows which packages and classes are covered by tests.<br/>
      <a href="https://app.codecov.io/gh/jguida941/contact-service-junit">
        Open the full-screen interactive sunburst on Codecov »
      </a>
    </td>
  </tr>
</table>

<br/>

## Self-Hosted Mutation Runner Setup
- Register a runner per GitHub's instructions (Settings -> Actions -> Runners -> New self-hosted runner). Choose macOS/Linux + architecture.
- Install + configure:
  - Go to your repository on GitHub
  - Navigate to Settings -> Actions -> Runners -> New self-hosted runner
  - Select your OS (macOS for Mac, Linux for Linux) and architecture (x64 for Intel, arm64 for Apple Silicon)
  - Follow GitHub's provided commands to download and configure the runner.

  **For macOS**:
  ```bash
  # Create runner directory
  mkdir actions-runner && cd actions-runner

  # Download the latest runner package (check GitHub for exact URL as it includes version)
  # For Intel Mac:
  curl -o actions-runner-osx-x64-2.321.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.321.0/actions-runner-osx-x64-2.321.0.tar.gz
  # For Apple Silicon Mac:
  # curl -o actions-runner-osx-arm64-2.321.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.321.0/actions-runner-osx-arm64-2.321.0.tar.gz

  # Extract the installer
  tar xzf ./actions-runner-osx-*.tar.gz

  # Configure the runner (get token from GitHub UI)
  ./config.sh --url https://github.com/jguida941/contact-service-junit --token YOUR_TOKEN_FROM_GITHUB

  # Set MAVEN_OPTS permanently (choose based on your shell)
  # For zsh (default on modern macOS):
  echo 'export MAVEN_OPTS="--enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true"' >> ~/.zshrc
  source ~/.zshrc
  # For bash:
  # echo 'export MAVEN_OPTS="--enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true"' >> ~/.bash_profile
  # source ~/.bash_profile

  # Start the runner
  ./run.sh
  ```
Leave `./run.sh` running so the `mutation-test` job can execute on your machine.<br>
When you're done, press Ctrl+C to stop the runner.

**Workflow toggle** - the `mutation-test` job only runs when the repository variable `RUN_SELF_HOSTED` is set to `true`.
- Default (variable unset/false): the job is skipped so GitHub-hosted runners finish cleanly even if your machine is offline.
- When you want to run mutation tests: start the runner and set `Settings → Secrets and variables → Actions → Variables → RUN_SELF_HOSTED = true`, then re-run the workflow.
- Turn the variable back to `false` (or delete it) when you shut down the runner, so future workflows don’t wait for a machine that isn’t listening.

## How to Use This Repository
If you're working through CS320 (or just exploring the project), the recommended flow is:
1. Read the requirements in `docs/requirements/contact-requirements/` so you understand the contact rules and service behavior.
2. Study `Contact.java`, `ContactService.java`, and `Validation.java`, then jump into the paired tests (`ContactTest.java` and `ContactServiceTest.java`) to see every rule exercised.
3. Run `mvn verify` and inspect the JUnit (Contact + service suites), JaCoCo, PITest, and dependency reports in `target/` to understand how the quality gates evaluate the project.
4. Experiment by breaking a rule on purpose, rerunning the build, and seeing which tests/gates fail, then fix the tests or code (and add/update assertions in both test classes) as needed.

## Resources & References
| Item                                                                               | Purpose                                                                  |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| [docs/requirements/contact-requirements/](docs/requirements/contact-requirements/) | Contact assignment requirements and acceptance criteria.                 |
| [docs/requirements/task-requirements/](docs/requirements/task-requirements/)       | Task assignment requirements and acceptance criteria.                    |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)                                       | Master document with scope, phases, checklist, and code examples.        |
| [docs/INDEX.md](docs/INDEX.md)                                                     | Full file/folder navigation for the repository.                          |
| [GitHub Actions workflows](.github/workflows)                                      | CI/CD definitions described above.                                       |
| [config/checkstyle](config/checkstyle)                                             | Checkstyle rules enforced in CI.                                         |
| [Java 17 (Temurin)](https://adoptium.net/temurin/releases/)                        | JDK used locally and in CI.                                              |
| [Apache Maven](https://maven.apache.org/)                                          | Build tool powering the project.                                         |
| [JUnit 5](https://junit.org/junit5/)                                               | Test framework leveraged in `ContactTest` and `ContactServiceTest`.      |
| [AssertJ](https://assertj.github.io/doc/)                                          | Fluent assertion library used across the test suites.                    |
| [PITest](https://pitest.org/)                                                      | Mutation testing engine enforced in CI.                                  |
| [OWASP Dependency-Check](https://jeremylong.github.io/DependencyCheck/)            | CVE scanning tool wired into Maven/CI.                                   |
| [Checkstyle](https://checkstyle.sourceforge.io/)                                   | Style/complexity checks.                                                 |
| [SpotBugs](https://spotbugs.github.io/)                                            | Bug pattern detector.                                                    |
| [CodeQL](https://codeql.github.com/docs/)                                          | Semantic security analysis.                                              |

## License
Distributed under the MIT License. See [LICENSE](LICENSE) for details.
