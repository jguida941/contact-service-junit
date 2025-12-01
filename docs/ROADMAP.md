# Roadmap

> For full details, checklist, and code examples, see **[REQUIREMENTS.md](REQUIREMENTS.md)**.

## Implementation Order

```
Phase 0   ✅ Pre-API fixes (defensive copies, date validation)
Phase 1   ✅ Spring Boot scaffold (layered packages, actuator, smoke tests)
Phase 2   ✅ REST API + DTOs + OpenAPI (642 tests, 648 with ITs; PIT mutation 94% / 96%+ store coverage)
Phase 2.5 ✅ API fuzzing in CI (Schemathesis 30,668 tests, ZAP-ready artifacts)
Phase 3   ✅ Persistence (Spring Data JPA, Flyway, Postgres, Testcontainers)
Phase 4   ✅ React UI (Vite + React 19 + Tailwind v4 + shadcn/ui, full CRUD, Maven-integrated)
Phase 5   ✅ Security + Observability
Phase 5.5 ✅ DAST + auth tests
Phase 6   → Docker packaging + CI
Phase 7   → UX polish
```

Phase 4 note: Full CRUD UI with React Hook Form + Zod validation. `mvn package` builds UI via frontend-maven-plugin and packages into single JAR. Run with `java -jar target/*.jar`. Frontend tests: Vitest + RTL (22 tests) and Playwright E2E (5 tests). See ADR-0025..0028 for design decisions.

Phase 3 note: legacy `getInstance()` access now reuses the Spring-managed proxies (or the in-memory fallback pre-boot) without proxy unwrapping; behavior tests verify both entry points stay in sync.

Phase 5 focus: End-to-end authentication/authorization, per-tenant data isolation, Docker/compose deployment with health probes, structured logging/metrics, rate limiting, threat modeling, and a documented ZAP baseline scan. See `docs/REQUIREMENTS.md` for the full checklist (JWT login, SPA-only CORS, security headers, bucket4j throttling, Prometheus/Actuator exposure, CI health checks, and runbook updates). The most recent hardening explicitly sets `SameSite=Lax` (plus inherits the secure-cookie flag) on the SPA-visible `XSRF-TOKEN` cookie so ZAP rule 10054 no longer fails.

**Future Option (after Phase 7): Project/Task tracker evolution.** `docs/IMPLEMENTATION_PLAN_OPTION_C.md` captures a multi-phase expansion (projects, richer tasks, appointments linkage, assignments). That work stays blocked until Phases 5–7 plus CI security gates are green, and it will require new ADR(s) for validation changes and new aggregates before implementation begins.

## CI/CD Security Stages

```
Phase 8  → ZAP in CI
Phase 9  → API fuzzing in CI
Phase 10 → Auth/role tests in CI
```

## Quick Links

- **Master Document**: [REQUIREMENTS.md](REQUIREMENTS.md)
- **ADR Index**: [adrs/README.md](adrs/README.md)
- **CI/CD Plan**: [ci-cd/ci_cd_plan.md](ci-cd/ci_cd_plan.md)
