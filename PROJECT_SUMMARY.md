# Contact Suite - Project Summary

> **Last Updated:** December 6, 2025

## Overview

Full-stack contact management application built with Spring Boot 4.0.0 and React 19. Features JWT authentication, PostgreSQL persistence via Flyway migrations, and comprehensive test coverage with mutation testing.

## Quick Stats

| Metric | Value |
|--------|-------|
| Java Files | 178 |
| Test Files | 91 |
| Test Executions | 1107 |
| Line Coverage | 90% |
| Mutation Coverage | 84% |
| TypeScript Files | 56 |
| ADRs | 54 |
| Flyway Migrations | 17 (V1-V14, V16-V18) |
| Design Notes | 26 |

## Tech Stack

### Backend
- **Framework:** Spring Boot 4.0.0
- **Database:** PostgreSQL 16 (Testcontainers for tests)
- **ORM:** Spring Data JPA + Flyway migrations
- **Security:** JWT authentication, Spring Security 6
- **Build:** Maven with JaCoCo, PITest, SpotBugs, Checkstyle

### Frontend
- **Framework:** React 19 + TypeScript
- **Styling:** Tailwind CSS v4 + shadcn/ui
- **State:** TanStack Query + React Router v7
- **Build:** Vite + frontend-maven-plugin
- **Testing:** Vitest + Playwright

## Package Structure

```
src/main/java/contactapp/
├── Application.java          # Spring Boot entry point
├── api/                      # REST controllers + DTOs
├── config/                   # Rate limiting, logging, CORS
├── domain/                   # Domain entities (Contact, Task, Appointment, Project)
├── persistence/              # JPA entities, repositories, mappers, stores
├── security/                 # JWT, User entity, auth filters
└── service/                  # Business logic layer
```

## Domain Entities

| Entity | Description |
|--------|-------------|
| **Contact** | Core entity with name, phone, address validation |
| **Task** | Linked to projects, with status (TODO/IN_PROGRESS/DONE), due dates, assignees |
| **Appointment** | Linked to tasks/projects, date validation (future-only) |
| **Project** | Status tracking (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED) |
| **User** | JWT authentication, role-based access (USER/ADMIN) |

## Database Migrations

| Migration | Purpose |
|-----------|---------|
| V1 | Create contacts table |
| V2 | Create tasks table |
| V3 | Create appointments table |
| V4 | Create users table |
| V5 | Add user_id foreign keys to contacts/tasks/appointments |
| V6 | Add surrogate numeric keys and uniqueness constraints |
| V7 | Add version columns (optimistic locking) |
| V8 | Create projects table |
| V9 | Enhance tasks with status and due date |
| V10 | Add project_id to tasks |
| V11 | Add project/task links to appointments |
| V12 | Add assignee to tasks |
| V13 | Create project_contacts junction table |
| V14 | Add archived flag to appointments |
| V16 | Migrate users to UUID identifiers |
| V17 | Create refresh tokens table |
| V18 | Add archived flag to tasks |

## Quality Gates

| Tool | Purpose | Threshold |
|------|---------|-----------|
| JaCoCo | Line coverage | 80% (75% H2 lane) |
| PITest | Mutation testing | 70% |
| Checkstyle | Code style enforcement | 0 violations |
| SpotBugs | Bug pattern detection | 0 findings |
| OWASP Dependency-Check | CVE scanning | CVSS < 7 |
| CodeQL | Security analysis | 0 findings |

## CI/CD Pipeline

- **Matrix:** Ubuntu + Windows × JDK 17 + 21
- **Jobs:** build-test, api-fuzz (Schemathesis), container-test, mutation-test (self-hosted)
- **Artifacts:** JaCoCo reports, PITest reports, QA dashboard
- **Coverage:** Codecov integration

## Key Documentation

| Document | Location |
|----------|----------|
| README | [README.md](README.md) |
| Requirements | [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) |
| Roadmap | [docs/roadmaps/ROADMAP.md](docs/roadmaps/ROADMAP.md) |
| ADR Index | [docs/adrs/README.md](docs/adrs/README.md) |
| Design Notes | [docs/design-notes/notes/](docs/design-notes/notes/) |
| CI/CD Operations | [docs/design-notes/notes/ci-cd-operations-notes.md](docs/design-notes/notes/ci-cd-operations-notes.md) |

## Roadmap Status

| Phase | Status |
|-------|--------|
| Phase 0: Pre-API fixes | Complete |
| Phase 1: Spring Boot scaffold | Complete |
| Phase 2: REST API + DTOs + OpenAPI | Complete |
| Phase 2.5: API fuzzing | Complete |
| Phase 3: Persistence (JPA, Flyway, Postgres) | Complete |
| Phase 4: React UI | Complete |
| Phase 5: Security + Observability | Complete |
| Phase 5.5: DAST + auth tests | Complete |
| Phase 6: Docker packaging + CI | Complete |
| Phase 7: UX polish | Complete |
| Phase 8-11: CI/CD Security Stages | Complete |

## Local Development

```bash
# Backend only (H2)
mvn spring-boot:run

# Full stack with UI
mvn package && java -jar target/*.jar

# Full test suite (requires Docker)
mvn verify -DskipITs=false

# Fast local build
mvn -Ddependency.check.skip=true -Dpit.skip=true verify
```

## Recent Changes

- Updated metrics to latest reports (1107 tests, 90% line coverage, 84% mutation)
- Removed duplicate sections (React UI, Testcontainers Strategy)
- Added 7 design-notes reference docs
- Fixed docs/CI-CD link case sensitivity (git tracked as CI-CD, not ci-cd)
