# Architecture Decision Records

This directory contains all Architecture Decision Records (ADRs) for the project.

## Organization

- **ADR-0001 to ADR-0054**: CS320 Foundation + Production Auth + Frontend Fixes + Security Audit (Complete)
- **ADR-0055+**: Jira-like Evolution (Future phases from [FUTURE_ROADMAP.md](../roadmaps/FUTURE_ROADMAP.md))

---

## CS320 Foundation (ADR-0001 to ADR-0054) ✅

All 54 foundation ADRs are **complete** and document the journey from a simple contact manager to a production-grade full-stack application with temporal data handling, production security, timezone-safe frontend, and comprehensive security hardening.

### Domain & Validation (ADR-0001 to ADR-0003)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](ADR-0001-validation-normalization.md) | Validation Normalization Policy | Accepted |
| [ADR-0002](ADR-0002-contact-service-storage.md) | Contact Service Storage Model | Accepted |
| [ADR-0003](ADR-0003-update-atomicity.md) | Update Atomicity Strategy | Accepted |

### CI/CD & Quality (ADR-0004 to ADR-0009)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0004](ADR-0004-ci-gates-and-thresholds.md) | CI Gates and Quality Thresholds | Accepted |
| [ADR-0005](ADR-0005-spotbugs-runtime-support.md) | SpotBugs Runtime Support | Accepted |
| [ADR-0006](ADR-0006-docs-structure.md) | Documentation Structure | Accepted |
| [ADR-0007](ADR-0007-task-entity-and-service.md) | Task Entity and Service | Accepted |
| [ADR-0008](ADR-0008-ci-metrics-summary-script.md) | CI Metrics Summary Script | Accepted |
| [ADR-0009](ADR-0009-test-strategy.md) | Test Strategy and Full Range Coverage | Accepted |

### Entity Implementation (ADR-0010 to ADR-0013)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0010](ADR-0010-task-validation-and-tests.md) | Task Validation and Test Coverage | Accepted |
| [ADR-0011](ADR-0011-taskservice-singleton-and-crud-tests.md) | TaskService Singleton and CRUD Tests | Accepted |
| [ADR-0012](ADR-0012-appointment-validation-and-tests.md) | Appointment Validation and Test Coverage | Accepted |
| [ADR-0013](ADR-0013-appointmentservice-singleton-and-crud-tests.md) | AppointmentService Singleton and CRUD Tests | Accepted |

### Backend Architecture (ADR-0014 to ADR-0024)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0014](ADR-0014-backend-framework-and-persistence-stack.md) | Backend Framework and Persistence Stack | Accepted |
| [ADR-0015](ADR-0015-database-choice-and-profiles.md) | Database Choice and Environment Profiles | Accepted |
| [ADR-0016](ADR-0016-api-style-and-contract.md) | API Style and Contract | Accepted |
| [ADR-0017](ADR-0017-frontend-stack-and-application-shell.md) | Frontend Stack and Application Shell | Accepted |
| [ADR-0018](ADR-0018-authentication-and-authorization-model.md) | Authentication and Authorization Model | Accepted |
| [ADR-0019](ADR-0019-deployment-and-packaging.md) | Deployment and Packaging | Accepted |
| [ADR-0020](ADR-0020-spring-boot-scaffold.md) | Spring Boot Scaffold | Accepted |
| [ADR-0021](ADR-0021-rest-api-implementation.md) | REST API Implementation | Accepted |
| [ADR-0022](ADR-0022-custom-error-controller.md) | Custom Error Controller for JSON Errors | Accepted |
| [ADR-0023](ADR-0023-api-fuzzing-schema-compliance.md) | API Fuzzing Findings & Schema Compliance | Accepted |
| [ADR-0024](ADR-0024-persistence-implementation.md) | Persistence Implementation Strategy | Accepted |

### Frontend & UI (ADR-0025 to ADR-0028)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0025](ADR-0025-ui-component-library.md) | UI Component Library Selection | Accepted |
| [ADR-0026](ADR-0026-theme-system-and-design-tokens.md) | Theme System and Design Tokens | Accepted |
| [ADR-0027](ADR-0027-application-shell-layout.md) | Application Shell Layout Pattern | Accepted |
| [ADR-0028](ADR-0028-frontend-backend-build-integration.md) | Frontend-Backend Build Integration | Accepted |

### Development Philosophy (ADR-0029 to ADR-0035)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0029](ADR-0029-ci-as-quality-gate.md) | CI as Quality Gate Philosophy | Accepted |
| [ADR-0030](ADR-0030-pattern-first-development.md) | Pattern-First Development with AI Assistance | Accepted |
| [ADR-0031](ADR-0031-mutation-testing-for-test-quality.md) | Mutation Testing to Validate Test Strength | Accepted |
| [ADR-0032](ADR-0032-two-layer-validation.md) | Two-Layer Validation Strategy | Accepted |
| [ADR-0033](ADR-0033-transaction-management.md) | Transaction Management with @Transactional | Accepted |
| [ADR-0034](ADR-0034-mapper-pattern.md) | Mapper Pattern for Entity-Domain Separation | Accepted |
| [ADR-0035](ADR-0035-boolean-return-api.md) | Boolean Return API for Service Methods | Accepted |

### Security & Observability (ADR-0036 to ADR-0044)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0036](ADR-0036-admin-dashboard-role-based-ui.md) | Admin Dashboard and Role-Based UI | Accepted |
| [ADR-0037](ADR-0037-user-entity-validation-and-tests.md) | User Entity Validation and Test Coverage | Accepted |
| [ADR-0038](ADR-0038-authentication-implementation.md) | Authentication Implementation (Phase 5) | Accepted |
| [ADR-0039](ADR-0039-phase5-security-observability.md) | Phase 5 Security and Observability | Accepted |
| [ADR-0040](ADR-0040-request-tracing-and-logging.md) | Request Tracing and Logging Infrastructure | Accepted |
| [ADR-0041](ADR-0041-pii-masking-in-logs.md) | PII Masking in Log Output | Accepted |
| [ADR-0042](ADR-0042-docker-containerization-strategy.md) | Docker Containerization Strategy | Accepted |
| [ADR-0043](ADR-0043-httponly-cookie-authentication.md) | HttpOnly Cookie Authentication | Accepted |
| [ADR-0044](ADR-0044-optimistic-locking.md) | Optimistic Locking for Concurrent Updates | Accepted |

### Project Evolution & Testing (ADR-0045 to ADR-0049)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0045](ADR-0045-project-task-tracker-evolution.md) | Project/Task Tracker Evolution (6 Phases) | Accepted |
| [ADR-0046](ADR-0046-test-coverage-improvements.md) | Test Coverage Improvements - Store and Mapper | Accepted |
| [ADR-0047](ADR-0047-test-isolation-cleanup-utility.md) | Test Isolation and Cleanup Utility | Accepted |
| [ADR-0048](ADR-0048-testcontainers-single-container-lifecycle.md) | Testcontainers Single Container Lifecycle | Accepted |
| [ADR-0049](ADR-0049-spring-security-7-spa-csrf.md) | Spring Security 7 SPA CSRF Configuration | Accepted |

### Temporal Data Handling (ADR-0050)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0050](ADR-0050-domain-reconstitution-pattern.md) | Domain Reconstitution Pattern | Accepted |

### Developer Tooling (ADR-0051)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0051](ADR-0051-unified-cli-tool.md) | Unified CLI Tool (`./cs`) | Accepted |

### Production Security (ADR-0052)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0052](ADR-0052-production-auth-system.md) | Production-Grade Secure Authentication System | Implemented |

> **Implementation Complete (2025-12-06):** All phases implemented - UUID migration, JWT hardening, HTTPS setup, refresh tokens, token fingerprinting. 1107 tests pass.

### Frontend Improvements (ADR-0053)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0053](ADR-0053-timezone-safe-date-parsing.md) | Timezone-Safe Date Parsing in Frontend | Accepted |

> **Bug Fix (2025-12-06):** Fixed critical date display bug where dates shifted backwards by one day in negative UTC offset timezones. Added `dateUtils.ts` with `parseISO()` for consistent timezone handling.

### Security Audit (ADR-0054)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0054](ADR-0054-security-audit-december-2025.md) | Security Audit Findings - December 2025 | Accepted |

> **Security Hardening (2025-12-06):** Comprehensive security audit implementing JWT token IDs (JTI), fingerprint enforcement config, rate limit key prefixing, and deprecation of user-isolation-bypassing methods. ADR-0054 includes detailed CSRF configuration documentation with trade-off analysis between explicit configuration, `.spa()`, and per-cookie SameSite approaches (all equally valid).

---

## Jira-like Evolution (ADR-0055+) 📋

Future ADRs for evolving the application into a full project management platform. See [FUTURE_ROADMAP.md](../roadmaps/FUTURE_ROADMAP.md) for the complete roadmap.

### Phase 8: Task Enhancements (Planned)

| ADR | Title | Phase | Status |
|-----|-------|-------|--------|
| ADR-0055 | Task Types (Bug, Story, Epic, Subtask) | 8 | Planned |
| ADR-0056 | Priority Levels (P0-P4) | 8 | Planned |
| ADR-0057 | Story Points Implementation | 8 | Planned |
| ADR-0058 | Labels and Tags System | 8 | Planned |

### Phase 9: Sprint Management (Planned)

| ADR | Title | Phase | Status |
|-----|-------|-------|--------|
| ADR-0059 | Sprint Entity Design | 9 | Planned |
| ADR-0060 | Sprint Board UI | 9 | Planned |
| ADR-0061 | Velocity Tracking | 9 | Planned |

### Phase 10: Activity & Comments (Planned)

| ADR | Title | Phase | Status |
|-----|-------|-------|--------|
| ADR-0062 | Comment System Design | 10 | Planned |
| ADR-0063 | Activity Feed Implementation | 10 | Planned |
| ADR-0064 | @Mentions and Notifications | 10 | Planned |

### Future Phases (11-17)

See [FUTURE_ROADMAP.md](../roadmaps/FUTURE_ROADMAP.md) for:
- Phase 11: Kanban Board UI
- Phase 12: Reporting & Analytics
- Phase 13: Notifications & Real-time
- Phase 14: Epic & Roadmap View
- Phase 15: Custom Workflows
- Phase 16: Integrations (GitHub, Slack)
- Phase 17: Teams & Permissions

---

## ADR Template

When creating new ADRs, use this template:

```markdown
# ADR-00XX: [Title]

**Status:** Proposed | **Date:** YYYY-MM-DD | **Owners:** [Name]

**Related:** [Links to related ADRs, files, or docs]

## Context
[Why is this decision needed?]

## Decision
[What is the decision?]

## Consequences
[What are the implications?]

## Alternatives Considered
[What other options were evaluated?]
```

---

## Related Documentation

- [FUTURE_ROADMAP.md](../roadmaps/FUTURE_ROADMAP.md) - Detailed feature specifications for phases 8-17
- [ROADMAP.md](../roadmaps/ROADMAP.md) - CS320 phase completion status
- [REQUIREMENTS.md](../REQUIREMENTS.md) - Master requirements document
- [Design Notes](../design-notes/README.md) - Informal implementation notes
