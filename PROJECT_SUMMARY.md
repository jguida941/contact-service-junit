# Multi-Entity Contact Suite - Project Summary

> A full-stack app that started as a simple CS320 assignment and grew into a complete system with a Spring Boot API, React UI, real database, tests, and CI/CD.

---

## Project Overview

**What Started As:** A basic Java contact management exercise using in-memory HashMaps
**What It Became:** A full-stack app with Spring Boot, React, PostgreSQL, tests, and GitHub Actions CI

**Tech Stack:**
- **Backend:** Spring Boot 3, Spring Data JPA, Hibernate, Flyway
- **Frontend:** React, Vite, TypeScript, Tailwind CSS, shadcn/ui
- **Database:** PostgreSQL (prod), H2/Testcontainers (tests)
- **Testing:** JUnit 5, PITest (mutation testing), JaCoCo, Schemathesis (API fuzzing)
- **CI/CD:** GitHub Actions, CodeQL, OWASP Dependency-Check, SpotBugs

---

## What I Built

### 1. Domain Layer - Three Business Entities

Built three fully validated domain entities following fail-fast principles:

| Entity          | Purpose           | Validation Rules                                                             |
|-----------------|-------------------|------------------------------------------------------------------------------|
| **Contact**     | Person management | ID (1-10 chars), Names (1-10 chars), Phone (10 digits), Address (1-30 chars) |
| **Task**        | Task tracking     | ID (1-10 chars), Name (1-20 chars), Description (1-50 chars)                 |
| **Appointment** | Scheduling        | ID (1-10 chars), Date (not in past), Description (1-50 chars)                |

**Key Design Decisions:**
- **Immutable identifiers** - IDs are set once at construction and never change
- **Centralized validation** - All validation logic lives in a single `Validation.java` helper class
- **Atomic updates** - Multi-field updates validate all fields first, then apply changes together
- **Defensive copies** - Getters return copies where it matters (like Date objects) to prevent mutation

### 2. Service Layer - Business Logic & Persistence Abstraction

Created services with a dual-mode architecture supporting both modern Spring DI and legacy singleton patterns:

```
Controller -> Service -> Store Interface -> JPA/In-Memory Implementation
```

**Why This Approach:**
- Backward compatibility with legacy `getInstance()` callers
- Clean dependency injection for modern code
- Easy testing with both H2 and real PostgreSQL via Testcontainers
- Transactional guarantees with `@Transactional` annotations

### 3. REST API Layer - Production-Ready Endpoints

Built a complete REST API at `/api/v1/{contacts,tasks,appointments}`:

| Endpoint     | Operations                 | Features                           |
|--------------|----------------------------|------------------------------------|
| Contacts     | CRUD (POST/GET/PUT/DELETE) | Bean Validation, DTO mapping       |
| Tasks        | CRUD (POST/GET/PUT/DELETE) | Bean Validation, DTO mapping       |
| Appointments | CRUD (POST/GET/PUT/DELETE) | Date validation, timezone handling |

**API Features:**
- Request/Response DTOs with Jakarta Bean Validation
- Global exception handler mapping to proper HTTP status codes (400, 404, 409)
- Custom error controller ensuring ALL errors return JSON (including Tomcat-level errors)
- OpenAPI/Swagger documentation auto-generated via springdoc-openapi

### 4. Persistence Layer - Database Integration

Implemented proper data persistence with:

- **JPA Entities** - Mapped to PostgreSQL tables
- **Flyway Migrations** - Version-controlled schema management
- **Store Abstraction** - Clean interface between services and data access
- **Multi-Profile Support:**
  - `default` - H2 in-memory (PostgreSQL compatibility mode)
  - `dev` - Local PostgreSQL
  - `test` - H2 with Flyway for CI
  - `integration` - Testcontainers ephemeral PostgreSQL
  - `prod` - Production PostgreSQL via environment variables

### 5. React UI - Modern Frontend

Built a complete React frontend with:

**Pages:**
- Overview/Dashboard
- Contacts Management (full CRUD)
- Tasks Management (full CRUD)
- Appointments Management (full CRUD)
- Settings (profile + appearance)
- Help/Documentation

**Frontend Features:**
- React Hook Form + Zod validation mirroring backend rules
- TanStack Query for data fetching/caching
- Global theme system (5 themes, light/dark mode)
- Auth-aware routing with `/login`, `RequireAuth`, and `PublicOnlyRoute` to ensure JWTs are issued before the SPA hits `/api/v1/**`
- Responsive sidebar navigation
- shadcn/ui component library

### 6. Quality & Testing - Comprehensive Coverage

| Metric | Value |
|--------|-------|
| **Total Tests** | 642 (648 with ITs) |
| **Mutation Coverage (PITest)** | 94% (615/656 mutants killed) |
| **Line Coverage (JaCoCo)** | 96%+ stores, 95%+ mappers |
| **Static Analysis** | SpotBugs clean |
| **API Fuzzing** | 30,668 Schemathesis tests |

**Test Types:**
- Unit tests for domain entities and validation
- Service tests with H2 + Flyway
- Controller tests with MockMvc
- Integration tests with Testcontainers (real PostgreSQL)
- Legacy singleton compatibility tests
- API fuzzing with Schemathesis

### 7. CI/CD Pipeline

Automated quality gates via GitHub Actions:

```
Compile -> Unit Tests -> Checkstyle -> SpotBugs -> JaCoCo -> PITest -> OWASP Dependency-Check -> CodeQL
```

**CI Features:**
- Automated badge generation for README
- API fuzzing workflow with Schemathesis
- CodeQL security analysis
- Dependabot for dependency updates
- Quality dashboard with metrics aggregation

---

## Why I Made These Decisions

### 44 Architecture Decision Records (ADRs)

Every major decision is documented with context, rationale, and consequences:

| ADR | Decision | Why |
|-----|----------|-----|
| ADR-0001 | Centralized validation | Eliminated duplicate logic, consistent error messages, easier mutation testing |
| ADR-0002 | Store abstraction | Decoupled services from storage implementation, enables testing |
| ADR-0003 | Atomic updates | Prevents partial state corruption, all-or-nothing semantics |
| ADR-0014 | Spring Boot 3 | Mature ecosystem, auto-configured REST, observability hooks |
| ADR-0015 | PostgreSQL + H2/Testcontainers | Production-grade DB with fast test execution |
| ADR-0016 | REST + OpenAPI | Industry standard, auto-documented, consumer-friendly |
| ADR-0017 | React + Vite + TypeScript | Fast dev cycle, type safety, modern tooling |
| ADR-0022 | Custom error controller | All errors return JSON, not HTML (API-first) |
| ADR-0024 | Persistence abstraction | Legacy compatibility while moving to proper database |
| ADR-0025 | shadcn/ui | High quality components, fully customizable, no vendor lock-in |
| ADR-0028 | Single JAR packaging | Frontend bundled into Spring Boot JAR, same-origin deployment |
| **ADR-0029** | **CI as quality gate** | **Treat CI as strict validation - code isn't ready until pipeline passes** |
| **ADR-0030** | **Pattern-first with AI** | **Build first entity manually, use AI to mirror pattern for consistency** |
| **ADR-0031** | **Mutation testing** | **Prove tests catch bugs, not just execute code** |
| **ADR-0032** | **Two-layer validation** | **DTO + domain validation for defense in depth** |
| **ADR-0033** | **@Transactional** | **Database operations are all-or-nothing** |
| **ADR-0034** | **Mapper pattern** | **Separate domain from JPA, re-validate on load** |
| **ADR-0035** | **Boolean return API** | **Return false for expected failures, throw for bugs** |

### Key Engineering Principles Applied

1. **Fail-Fast Validation** - Invalid data throws immediately with precise messages
2. **Single Responsibility** - Validation, persistence, and business logic are separated
3. **Backward Compatibility** - Old singleton code still works while new code uses DI
4. **Multiple Layers of Validation** - Validation at domain, service, API, and database layers
5. **Test-Heavy Development** - Core tests written early (before CI), then used CI and mutation testing to grow coverage and add edge cases
6. **Documentation as Code** - ADRs capture decisions, not just what but why

---

## Project Statistics

| Category | Count |
|----------|-------|
| Java Production Classes | 62 |
| Java Test Classes | 51 |
| React Components | 40+ |
| ADRs | 44 |
| Database Migrations | 7 |
| CI/CD Workflows | 3 |
| Documentation Files | 70+ |

---

## What's Left To Do

### Phase 4: Frontend Tests ✅
- [x] Vitest + React Testing Library — 22 component tests (think “Jest for Vite”: it renders React components in jsdom and lets us assert on things like “does the Contact form show a validation message when fields are empty?”)
- [x] Playwright E2E — 5 happy-path tests (Playwright launches a real browser, visits the running app, and clicks through the list/create/edit/delete flow the way a human user would)

### Phase 5: Security + Observability ✅
- [x] JWT authentication with Spring Security
- [x] Role-based authorization (`@PreAuthorize`)
- [x] CORS configuration for SPA
- [x] Security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options)
- [x] Rate limiting for public exposure
- [x] Structured logging with correlation IDs
- [x] Metrics/tracing via Actuator/Micrometer

### Phase 5.5: DAST + Runtime Security ✅
- [x] OWASP ZAP baseline/API scan in CI
- [x] Auth/role integration tests (401/403 assertions)

### Phase 6: Packaging + CI
- [ ] Dockerfiles for backend and UI
- [ ] docker-compose for app + PostgreSQL
- [ ] Makefile/task runner for local dev
- [ ] Image publishing to GHCR

### Phase 7: UX Polish
- [ ] Search/pagination/sorting for large datasets
- [ ] Empty states and toast notifications
- [ ] Accessibility hardening
- [ ] Feature usage instrumentation

### Future Backlog Items
- Decommission legacy `getInstance()` singletons once all callers use DI
- OAuth2/SSO upgrade path beyond initial JWT auth
- WAF/API gateway selection for production deployment
- java.time migration (from java.util.Date)

---

## Skills Demonstrated

**Backend Development:**
- Spring Boot 3, Spring Data JPA, Hibernate
- RESTful API design with OpenAPI/Swagger
- Database migrations with Flyway
- Multi-environment configuration

**Frontend Development:**
- React 19 with hooks and functional components
- TypeScript for type safety
- Modern build tooling (Vite)
- State management with TanStack Query
- Form handling with React Hook Form + Zod
- Component libraries (shadcn/ui)
- Tailwind CSS for styling

**Testing & Quality:**
- JUnit 5 with parameterized tests
- Mutation testing with PITest (94% kill rate, 615/656 mutants)
- Integration testing with Testcontainers
- API fuzzing with Schemathesis
- Static analysis with SpotBugs and Checkstyle

**DevOps & CI/CD:**
- GitHub Actions workflows
- CodeQL security analysis
- OWASP Dependency-Check
- Automated badge generation
- Multi-profile application configuration

**Software Engineering Practices:**
- Architecture Decision Records (ADRs)
- Test-heavy development with mutation testing
- Fail-fast design
- Defensive programming
- Single responsibility principle
- Clean architecture with layered packages

---

## Key Design Decisions

> **Use this section to quickly recall key points**

### The 2-Sentence Pitch

"I built a full-stack contact management system with Spring Boot and React. It started as a simple CS320 assignment, but I evolved it into a production-grade app with 44 documented design decisions, 94% mutation test coverage (615/656 mutants killed), and a complete CI/CD pipeline."

### Main Components (4 pieces)

```
React 19 SPA  →  Spring Boot REST API  →  Service Layer  →  PostgreSQL
     ↓                    ↓                     ↓               ↓
  Zod + RHF        Bean Validation      @Transactional     Flyway migrations
```

### Design Decision Talk Tracks

Use this template: *"The problem was __. I looked at __. I chose __ because __. The tradeoff is __."*

---

**Decision 1: Layered Architecture (Domain → Service → API → Persistence)**

|              |                                                                                         |
|--------------|-----------------------------------------------------------------------------------------|
| **Problem**  | Needed to separate business logic from storage so I could test without a real database  |
| **Options**  | (1) Everything in controllers, (2) Two layers, (3) Full separation with interfaces      |
| **Chose**    | Full separation - domain stays pure, services handle logic, stores abstract persistence |
| **Tradeoff** | More classes, but I can swap H2 for PostgreSQL or mock stores in tests easily           |

---

**Decision 2: Two-Layer Validation (DTO + Domain)**

|              |                                                                                         |
|--------------|-----------------------------------------------------------------------------------------|
| **Problem**  | API needs clean 400 errors with field names, but domain objects should never be invalid |
| **Options**  | (1) Validate only at API layer, (2) Validate only in domain, (3) Both                   |
| **Chose**    | Both - Bean Validation on DTOs for nice API errors, domain constructors for safety net  |
| **Tradeoff** | Duplicate rules, but I use shared constants so they stay in sync                        |

---

**Decision 3: Pattern-First Development with AI**

|              |                                                                                                    |
|--------------|----------------------------------------------------------------------------------------------------|
| **Problem**  | I had three entities (Contact, Task, Appointment) that needed identical patterns                   |
| **Options**  | (1) Write all three manually, (2) Let AI generate everything, (3) Build one, AI mirrors            |
| **Chose**    | I built Contact manually to understand it, then had AI mirror the pattern for Task and Appointment |
| **Tradeoff** | I understand the patterns deeply for one entity; consistent code across all three                  |

---

**Decision 4: Mutation Testing (PITest)**

|              |                                                                                                                   |
|--------------|-------------------------------------------------------------------------------------------------------------------|
| **Problem**  | Line coverage can be 100% but tests might not actually catch bugs                                                 |
| **Options**  | (1) Trust line coverage, (2) Add mutation testing                                                                 |
| **Chose**    | PITest mutation testing - it changes code and checks if tests fail. 99% kill rate means tests actually catch bugs |
| **Tradeoff** | Slower CI (mutation tests take longer), but proves test quality, not just quantity                                |

---

**Decision 5: CI as Quality Gate**

|              |                                                                                               |
|--------------|-----------------------------------------------------------------------------------------------|
| **Problem**  | Easy to merge code that "works on my machine" but has issues                                  |
| **Options**  | (1) Manual review only, (2) Basic CI, (3) Strict CI that must pass                            |
| **Chose**    | Strict CI - compile, tests, checkstyle, SpotBugs, JaCoCo, PITest, OWASP, CodeQL all must pass |
| **Tradeoff** | Slower merges, but I trust that anything in main actually works                               |

---

### Security Decision (Planned)

|              |                                                                          |
|--------------|--------------------------------------------------------------------------|
| **Problem**  | API is currently open - no authentication                                |
| **Options**  | (1) Session cookies, (2) JWT tokens, (3) OAuth2/SSO                      |
| **Chose**    | Planning JWT with Spring Security - stateless, works well with React SPA |
| **Tradeoff** | Token revocation is harder than sessions, so I'll use short expiry times |

---

### Quick Answers to Common Questions

**"What does this system do?"**
> "It manages contacts, tasks, and appointments. Full CRUD operations through a React UI backed by a Spring Boot API with PostgreSQL persistence."

**"What are you most proud of?"**
> "The 94% mutation coverage. It's easy to get line coverage high, but mutation testing proves the tests actually catch bugs. PITest killed 615 out of 656 mutants."

**"What would you change?"**
> "I'd migrate from java.util.Date to java.time - the old Date API is awkward. Also, I'd implement token refresh earlier to reduce the complexity of session management."

**"How do you test it?"**
> "Unit tests for domain logic, service tests with H2, integration tests with Testcontainers running real PostgreSQL, and API fuzzing with Schemathesis that ran 30,000+ requests looking for edge cases."

**"Why Spring Boot instead of Node/Python?"**
> "The assignment started in Java with domain classes already written. Spring Boot let me add REST, validation, and persistence without rewriting everything. Plus good ecosystem for testing and security."

---

## Repository Structure

```
contact-suite-spring-react/
├── src/main/java/contactapp/
│   ├── domain/          # Contact, Task, Appointment, Validation
│   ├── service/         # Business logic with store abstraction
│   ├── api/             # REST controllers, DTOs, exception handling
│   ├── persistence/     # JPA entities, mappers, repositories, stores
│   └── config/          # Spring configuration
├── src/test/java/       # 642 tests (unit, integration, legacy, security)
├── ui/contact-app/      # React 19 + Vite + TypeScript frontend
├── docs/
│   ├── adrs/            # 44 Architecture Decision Records
│   ├── requirements/    # Domain specifications
│   └── REQUIREMENTS.md  # Master document with phased plan
├── .github/workflows/   # CI/CD pipelines
└── scripts/             # Development utilities
```

---

## How to Run

```bash
# Backend only (H2 in-memory)
mvn spring-boot:run

# Frontend development
cd ui/contact-app && npm install && npm run dev

# Full stack (one command)
python scripts/dev_stack.py

# Persist data between restarts (dev profile + Postgres)
python scripts/dev_stack.py --database postgres
# or manually:
docker compose -f docker-compose.dev.yml up -d
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/contactapp
export SPRING_DATASOURCE_USERNAME=contactapp
export SPRING_DATASOURCE_PASSWORD=contactapp
mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd ui/contact-app && npm run dev

# Production build (single JAR)
mvn package -DskipTests
java -jar target/cs320-contact-service-junit-1.0.0-SNAPSHOT.jar
```

---

## Links

- **Repository:** [github.com/jguida941/contact-suite-spring-react](https://github.com/jguida941/contact-suite-spring-react)
- **Original CS320 Branch:** [original-cs320](https://github.com/jguida941/contact-suite-spring-react/tree/original-cs320)
- **Swagger UI:** `/swagger-ui.html` (when running)
- **Health Check:** `/actuator/health`

---

*This project shows how I took a class assignment and turned it into a full-stack app with Spring Boot, React, a real database, lots of tests, and a CI pipeline.*
