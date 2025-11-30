# Roadmap

> For full details, checklist, and code examples, see **[REQUIREMENTS.md](REQUIREMENTS.md)**.

## Implementation Order

```
Phase 0   ✅ Pre-API fixes (defensive copies, date validation)
Phase 1   ✅ Spring Boot scaffold (layered packages, actuator, smoke tests)
Phase 2   ✅ REST API + DTOs + OpenAPI (296 tests, 100% mutation)
Phase 2.5 ✅ API fuzzing in CI (Schemathesis 30,668 tests, ZAP-ready artifacts)
Phase 3   ✅ Persistence (Spring Data JPA, Flyway, Postgres, Testcontainers)
Phase 4   → React UI
Phase 5   → Security + Observability
Phase 5.5 → DAST + auth tests
Phase 6   → Docker packaging + CI
Phase 7   → UX polish
```

Phase 3 note: legacy `getInstance()` access now reuses the Spring-managed proxies (or the in-memory fallback pre-boot) without proxy unwrapping; behavior tests verify both entry points stay in sync.

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
