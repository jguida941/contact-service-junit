# Demo Cheat Sheet

Quick reference for showcasing the project to recruiters.

---

## Quick Start

```bash
# Start everything (backend + frontend) - ONE COMMAND
./cs dev

# Or with persistent Postgres database
./cs dev --db postgres

# Access points:
# - API: http://localhost:8080
# - Swagger UI: http://localhost:8080/swagger-ui.html
# - React UI: http://localhost:5173

# Check service health
./cs health
```

---

## Show Quality Reports

```bash
# Run full build with all quality gates
./cs test

# Launch QA Dashboard (React app with all metrics)
./cs qa-dashboard

# Or view individual reports:
open target/site/jacoco/index.html      # 90% coverage
open target/pit-reports/index.html      # 84% mutation score
open target/spotbugs.html               # Static analysis
```

---

## Show Testing

```bash
# Run all quality checks (1107 tests)
./cs test

# Quick tests only (skip mutation/fuzzing)
./cs test --fast

# Individual test types:
./cs test --unit           # JUnit unit tests
./cs test --integration    # Real PostgreSQL via Testcontainers
./cs test --mutation       # PITest mutation testing
./cs test --security       # API fuzzing (30,000+ requests)
```

---

## Show Security Features

```bash
# 1. Rate Limiting Demo (login endpoint: 5 req/min)
for i in {1..7}; do
  echo "Attempt $i:"
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}'
done
# First 5: 401 (bad credentials)
# 6-7: 429 (rate limited)

# 2. JWT Cookie Demo
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}'
cat cookies.txt  # Shows httpOnly auth_token

# 3. CSRF Token Demo
curl -s http://localhost:8080/api/auth/csrf-token | jq
```

---

## Show Docker Deployment

```bash
# Database management via CLI
./cs db start      # Start Postgres container
./cs db status     # Check database status
./cs db logs       # View database logs
./cs db stop       # Stop container (data persists)
./cs db reset      # Reset database (destructive)

# Full production simulation
./cs prod-local    # Build JAR + run with prod settings

# Access pgAdmin (if using docker-compose directly)
docker-compose up -d pgadmin
open http://localhost:5050  # admin@contactapp.local / admin

# Cleanup
./cs db stop
```

---

## Show CI/CD (GitHub Actions)

```bash
# Push to trigger pipeline
git push origin main

# Watch in browser:
# 1. GitHub repo → Actions tab
# 2. Shows matrix: Ubuntu/Windows × Java 17/21
# 3. Quality gates: JaCoCo, PITest, SpotBugs, Checkstyle
# 4. API fuzzing workflow runs after
```

---

## Key Metrics to Mention

| Metric | Value |
|--------|-------|
| Test Classes | 91 |
| Test Executions | 1107 |
| Line Coverage | 90% (80% enforced) |
| Mutation Coverage | 84% (70% enforced) |
| ADRs | 54 |
| Flyway Migrations | 17 |
| Quality Gates | 7 enforced |

---

## Key Files to Show

| What | File |
|------|------|
| Architecture Decisions | `docs/adrs/` (54 ADRs) |
| Security Config | `src/main/java/contactapp/security/SecurityConfig.java` |
| Rate Limiting | `src/main/java/contactapp/config/RateLimitingFilter.java` |
| Domain Validation | `src/main/java/contactapp/domain/Validation.java` |
| CI Pipeline | `.github/workflows/java-ci.yml` |
| API Fuzzing | `.github/workflows/api-fuzzing.yml` |
| QA Dashboard | `ui/qa-dashboard/src/App.jsx` |

---

## Talking Points

### "What makes this project enterprise-grade?"

1. **Layered Architecture** - Clear separation between API, Service, Domain, Persistence
2. **Two-Layer Validation** - Bean validation at API, constructor validation in domain
3. **Mutation Testing** - Proves tests catch bugs, not just execute code
4. **Multi-tenant Security** - Per-user data isolation, JWT in httpOnly cookies
5. **54 ADRs** - Every major decision documented with rationale
6. **7 Quality Gates** - Enforced in CI, no exceptions

### "What security measures are implemented?"

1. JWT tokens in HttpOnly cookies (prevents XSS token theft)
2. BCrypt password hashing
3. Rate limiting (Bucket4j) - login, register, API
4. CSRF protection with Spring Security 7 SPA pattern
5. CSP headers, X-Frame-Options, etc.
6. OWASP Dependency-Check in CI
7. PII masking in logs

### "How do you ensure code quality?"

1. 90% line coverage (JaCoCo, 80% enforced)
2. 84% mutation score (PITest, 70% enforced)
3. SpotBugs static analysis (zero findings)
4. Checkstyle (zero violations)
5. API fuzzing with 30,000+ requests
6. Matrix CI (2 OS × 2 Java versions)
