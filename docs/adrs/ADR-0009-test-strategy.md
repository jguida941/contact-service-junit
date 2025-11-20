# ADR-0009: Test Strategy and Tooling

Status: Accepted  
Date: 2025-11-20  
Owners: Justin Guida  
Related: src/test/java/contactapp, docs/design-notes/notes/test-design-notes.md, docs/logs/CHANGELOG.md

## Context
- The assignment requires rigorous unit testing (length limits, null handling, CRUD behaviors) and our CI gates (JaCoCo, PITest) enforce quality thresholds.
- Without a defined strategy, tests could drift in style or coverage as we add more domain objects (Task) and services.
- We already have design notes describing why tests are split, but not a formal decision explaining how every new class should be tested going forward.

## Decision
- Maintain separate test classes per layer:
  - `*Test` for domain objects (constructor/setters/update trimming rules, invalid update atomicity).
  - `ValidationTest` for helper boundaries.
  - `*ServiceTest` for singleton/service behavior (CRUD, trimming, boolean contracts, reset helpers like `clearAll*`).
- Use AssertJ and JUnit 5 parameterized tests (`@CsvSource`) for expressive assertions and compact invalid-case coverage.
- Keep singleton state reset helpers (`clearAllContacts`, `clearAllTasks`) and ensure tests explicitly cover them so mutation testing catches missing `Map.clear()` calls.
- Run the full suite via Maven Surefire during `mvn verify`; enforce test quality with JaCoCo (coverage) and PITest (mutation) thresholds described in ADR-0004.

## Consequences
- Clear separation of test responsibilities keeps failures localized (domain vs. service vs. validation).
- Parameterized tests make it easy to add new invalid cases without copy/paste, but updating expected messages requires touching the CSV entries.
- Requiring explicit tests for reset helpers means new services must expose similar hooks, which adds minor boilerplate but keeps mutation coverage strong.
- PITest runs add time to CI, but they guarantee regressions (e.g., removing validation or `clearAll*`) are detected immediately.

## Alternatives considered
- **Single integration test suite** - rejected because it would blur the boundary between domain and service logic and make it harder to pinpoint failures.
- **Minimal “happy path only” tests** - rejected; they would pass coverage gates but provide little confidence or mutation resistance.
- **Different assertion libraries (plain JUnit assertions)** - workable but less readable; AssertJ’s fluent style and rich error messages were preferred.
