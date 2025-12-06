# Contact Suite CLI Tool

> **Status**: Phase 1 Complete (Core CLI ready for use)
> **Last Updated**: 2025-12-03
> **Note**: Phase 2 items (desktop dashboard, admin backend) are tracked separately.
> **Related ADRs**: [ADR-0051](../../adrs/ADR-0051-unified-cli-tool.md) (CLI tool - ADR-0050 is Domain Reconstitution Pattern)

---

## Problem Statement

There are too many entry points right now:

| Current Command | What It Does | Problems |
|-----------------|--------------|----------|
| `python scripts/dev_stack.py` | Start dev environment | Long command, cookie bug was present |
| `mvn spring-boot:run` | Start backend only | No frontend, need to set env vars |
| `cd ui/contact-app && npm run dev` | Start frontend only | Wrong directory, no backend |
| `docker compose -f docker-compose.dev.yml up` | Start Postgres | Verbose, easy to forget the file |
| `mvn test` | Run tests | Doesn't include frontend or mutation tests |
| `make test` | Run all tests | Unix only, 80+ targets to learn |
| `python scripts/api_fuzzing.py --start-app` | API fuzzing | Requires manual app management |

**Goal**: One CLI (`cs`) that provides a small set of predictable commands for local dev, local prod simulation, tests, and DB management.

> The CLI provides a single entry point for running, testing, and inspecting the Contact Suite locally. It wraps Maven, npm, Docker, and existing scripts so new contributors do not need to know all the internal commands.

---

## Command Surface (v1)

### `cs dev`

Start everything for local development.

```bash
cs dev                    # H2 in-memory database (fastest)
cs dev --db postgres      # Postgres via Docker
cs dev --backend-only     # Skip frontend
cs dev --frontend-only    # Skip backend (assumes backend already running)
```

**What it does**:
1. Start Postgres container if `--db postgres` and container not already running
2. Start Spring Boot backend with dev settings
3. Wait for `/actuator/health` to return UP
4. Start Vite frontend with hot reload
5. Print URLs: `http://localhost:8080` (API), `http://localhost:5173` (UI)

**Environment** (set automatically):
```
APP_AUTH_COOKIE_SECURE=false    # HTTP works on localhost
COOKIE_SECURE=false             # HTTP works on localhost
SPRING_PROFILES_ACTIVE=default  # or 'dev' with Postgres
CSP_RELAXED=true                # Vite HMR needs inline scripts
```

---

### `cs prod-local`

Simulate production environment locally.

```bash
cs prod-local                   # Build JAR, run with prod settings
cs prod-local --skip-build      # Use existing JAR in target/
cs prod-local --https           # Use self-signed cert (Phase 2 - not yet implemented)
```

**What it does**:
1. Run `mvn clean package -DskipTests` (unless `--skip-build`)
2. Start Postgres container if not running
3. Validate `JWT_SECRET` is set (abort with clear error if missing or is dev default)
4. Run JAR with production-like environment

**Environment** (set automatically):
```
APP_AUTH_COOKIE_SECURE=true     # Secure cookies required
COOKIE_SECURE=true              # Secure cookies required
SPRING_PROFILES_ACTIVE=prod     # Production profile
JWT_SECRET=${JWT_SECRET}        # REQUIRED - abort if empty
REQUIRE_SSL=false               # No TLS locally (use --https for TLS)
```

**Guard rails**:
- Abort with clear error if `JWT_SECRET` is not set or matches the dev default
- Warn if `SPRING_PROFILES_ACTIVE` is set to something unexpected

---

### `cs test`

Run all quality checks.

```bash
cs test                   # Run everything
cs test --unit            # JUnit unit tests only
cs test --integration     # Testcontainers integration tests only
cs test --frontend        # Vitest + Playwright
cs test --mutation        # PITest mutation testing
cs test --security        # OWASP dependency check + API fuzzing
cs test --fast            # Skip slow tests (mutation, fuzzing)
```

**What it does** (full run):
1. `mvn test` - JUnit unit tests
2. `mvn verify -DskipITs=false` - Integration tests with Testcontainers
3. `cd ui/contact-app && npm run test:run` - Vitest frontend tests
4. `mvn pitest:mutationCoverage` - Mutation testing
5. `python scripts/api_fuzzing.py --start-app` - API fuzzing
6. Exit non-zero on any failure

**Output**:
- Summary table of pass/fail per category
- Path to HTML reports (JaCoCo, PITest, Schemathesis)

---

### `cs qa-dashboard`

View quality metrics in browser.

```bash
cs qa-dashboard           # Build reports if needed, open browser
cs qa-dashboard --no-open # Just start server, don't open browser
```

**What it does**:
1. Check if `target/site/jacoco/` exists; if not, run `mvn verify`
2. Run `python scripts/serve_quality_dashboard.py`
3. Open browser to `http://localhost:<port>/qa-dashboard/index.html`

---

### `cs db`

Manage the development database.

```bash
cs db start               # Start Postgres container
cs db stop                # Stop Postgres container (with confirmation)
cs db status              # Show container status and connection info
cs db reset               # Drop and recreate database (with confirmation)
cs db logs                # Tail Postgres logs
cs db migrate             # Run Flyway migrations manually
```

**Guard rails**:
- `cs db stop` prompts: "Stop Postgres container? Data will persist. [y/N]"
- `cs db reset` prompts: "This will DELETE ALL DATA. Are you sure? [y/N]"

---

### `cs ci-local`

Reproduce CI pipeline locally.

```bash
cs ci-local               # Run same steps as GitHub Actions (ubuntu + JDK 17)
cs ci-local --fast        # Skip slow steps (mutation testing, OWASP scan)
```

**What it does** (mirrors `.github/workflows/java-ci.yml`):
1. `mvn clean verify` with all quality gates enabled
2. Build QA dashboard
3. Run API fuzzing
4. Print summary matching CI output format

**Environment** (mirrors CI):
```
SPRING_PROFILES_ACTIVE=default
NVD_API_KEY=${NVD_API_KEY}      # Optional, speeds up OWASP scans
```

---

### `cs health`

Quick health check of all services.

```bash
cs health                 # Check all services once
cs health --watch         # Continuous monitoring (refresh every 5s)
```

**Output**:
```
┌─────────────────────────────────────────────────┐
│ Contact Suite Health Check                      │
├─────────────┬────────┬──────────┬──────────────┤
│ Service     │ Status │ Latency  │ URL          │
├─────────────┼────────┼──────────┼──────────────┤
│ Backend API │ ✓ UP   │ 45ms     │ :8080        │
│ Frontend    │ ✓ UP   │ 12ms     │ :5173        │
│ PostgreSQL  │ ✓ UP   │ 8ms      │ :5432        │
│ Actuator    │ ✓ UP   │ 23ms     │ :8080/act... │
└─────────────┴────────┴──────────┴──────────────┘
```

---

### `cs dashboard`

Open the web-based DevOps dashboard.

```bash
cs dashboard              # Open browser to /admin/devops
```

**Note**: Requires ADMIN user login. The dashboard displays:
- Service health (same data as `cs health`)
- GitHub CI status
- Quality metrics (coverage, mutation score)
- Application logs (streaming)

---

## Out of Scope for v1

These are explicitly **not** included in the first version:

- Individual Makefile target wrappers (use `make` directly if needed)
- Docker image building/pushing (use `docker build` directly)
- GitHub Actions triggering (use `gh workflow run` directly)
- Production deployment (out of scope for local CLI)
- PyQt6 desktop dashboard via `cs dashboard --desktop` (Phase 2 feature)
- DevOps dashboard backend/frontend (separate work item)

---

## Implementation Details

### Language & Location

Keep it simple: extend `scripts/` rather than creating a new `/cli/` directory.

```
scripts/
├── cs_cli.py                  # NEW - Main CLI (typer-based)
├── runtime_env.py             # NEW - Environment configuration helpers
├── dev_stack.py               # EXISTING - Reuse functions
├── api_fuzzing.py             # EXISTING - Called by cs test
├── ci_metrics_summary.py      # EXISTING - Called by cs qa-dashboard
└── serve_quality_dashboard.py # EXISTING - Called by cs qa-dashboard

./cs                           # NEW - Shell shim at project root
```

### Shell Shim (`./cs`)

```bash
#!/usr/bin/env bash
set -euo pipefail
exec python3 -m scripts.cs_cli "$@"
```

### Dependencies

Minimal additions to existing Python environment:

```python
# scripts/requirements.txt
typer[all]>=0.9.0          # CLI framework with rich output
httpx>=0.27.0              # HTTP client for health checks
```

### Reuse Existing Code

The CLI imports and reuses functions from existing scripts:

```python
# scripts/cs_cli.py
from scripts.dev_stack import (
    _wait_for_backend,
    _start_process,
    _build_backend_env,
    _ensure_postgres,
)
```

### Configuration Precedence

1. **Explicit flags**: `--db postgres`, `--skip-build`
2. **Environment files**: `.env.dev`, `.env.prod-local` (if present)
3. **Environment variables**: `JWT_SECRET`, `SPRING_PROFILES_ACTIVE`
4. **Defaults**: H2 database, dev profile, insecure cookies

---

## Security & Profile Rules

### `cs dev` Environment

| Variable | Value | Reason |
|----------|-------|--------|
| `APP_AUTH_COOKIE_SECURE` | `false` | HTTP works on localhost |
| `COOKIE_SECURE` | `false` | HTTP works on localhost |
| `SPRING_PROFILES_ACTIVE` | `default` or `dev` | Development settings |
| `CSP_RELAXED` | `true` | Vite HMR needs inline scripts |
| `JWT_SECRET` | dev default OK | Not security-critical locally |

### `cs prod-local` Environment

| Variable | Value | Reason |
|----------|-------|--------|
| `APP_AUTH_COOKIE_SECURE` | `true` | Simulates production |
| `COOKIE_SECURE` | `true` | Simulates production |
| `SPRING_PROFILES_ACTIVE` | `prod` | Production settings |
| `CSP_RELAXED` | `false` | Strict CSP like production |
| `JWT_SECRET` | **REQUIRED** | Abort if missing or dev default |

### `cs ci-local` Environment

| Variable | Value | Reason |
|----------|-------|--------|
| Mirrors CI exactly | Same as `java-ci.yml` | Reproduce CI locally |
| `NVD_API_KEY` | Optional | Faster OWASP scans if set |

---

## Documentation Integration

### README Quick Start (to be updated)

```markdown
## Quick Start

# Start development environment
./cs dev

# Run all tests
./cs test

# View quality dashboard
./cs qa-dashboard

# Simulate production locally
./cs prod-local
```

### Developer Onboarding

Replace raw commands with CLI equivalents:

| Before | After |
|--------|-------|
| `python scripts/dev_stack.py --database postgres` | `./cs dev --db postgres` |
| `mvn test && mvn pitest:mutationCoverage` | `./cs test` |
| `docker compose -f docker-compose.dev.yml up -d` | `./cs db start` |
| `python scripts/serve_quality_dashboard.py` | `./cs qa-dashboard` |

---

## Implementation Roadmap

### Phase 1: Core Commands ✅ Complete

1. [x] Extract reusable helpers from `dev_stack.py` into `scripts/runtime_env.py`
2. [x] Create `scripts/cs_cli.py` with typer skeleton
3. [x] Create `./cs` shell shim at project root
4. [x] Implement `cs dev` (reuse `dev_stack.py` functions)
5. [x] Implement `cs db start|stop|status`
6. [x] Implement `cs health`

### Phase 2: Testing & Quality ✅ Complete

7. [x] Implement `cs test` (wrap Maven + npm + fuzzing scripts)
8. [x] Implement `cs qa-dashboard` (wrap existing script)
9. [x] Implement `cs ci-local`

### Phase 3: Production Simulation ✅ Complete

10. [x] Implement `cs prod-local`
11. [x] Add JWT_SECRET validation with clear error messages
12. [ ] Add `--https` flag with self-signed cert (optional, deferred)

### Phase 4: Documentation ✅ Complete

13. [x] Update README Quick Start section
14. [ ] Update DEMO.md for recruiters (deferred)
15. [x] Create ADR-0051: Unified CLI Tool (ADR-0050 is Domain Reconstitution Pattern)
16. [ ] Wire all docs to reference CLI commands (in progress)

### Phase 5: Dashboard (v2, optional)

17. [ ] Build missing Admin backend (AdminController, AdminService)
18. [ ] Add DevOps tab to Admin dashboard (web)
19. [x] Implement `cs dashboard` to open browser
20. [ ] Optional: PyQt6 desktop version

---

## Known Issues

### Admin Dashboard Backend Missing

The `AdminDashboard.tsx` frontend exists and is fully implemented, but the backend endpoints return 404:

| Endpoint | Status |
|----------|--------|
| `GET /api/v1/admin/users` | ❌ 404 - No controller |
| `GET /api/v1/admin/stats` | ❌ 404 - No controller |
| `GET /api/v1/admin/audit-log` | ❌ 404 - No controller |

**Decision**: This is tracked separately. The CLI `cs dashboard` will open the admin page, but the admin backend is a prerequisite that needs to be built first.

### Cookie Security Bug (FIXED)

The `dev_stack.py` script was missing `APP_AUTH_COOKIE_SECURE=false`, causing 403 Forbidden errors on localhost HTTP. This has been fixed and will be properly handled in `cs dev`.

---

## Command Reference Summary

| Command | Description |
|---------|-------------|
| `cs dev` | Start development environment (backend + frontend) |
| `cs dev --db postgres` | Start with Postgres instead of H2 |
| `cs prod-local` | Build and run production-like JAR |
| `cs test` | Run all quality checks |
| `cs test --fast` | Skip slow tests (mutation, fuzzing) |
| `cs qa-dashboard` | View quality metrics in browser |
| `cs db start` | Start Postgres container |
| `cs db stop` | Stop Postgres container |
| `cs db status` | Check database status |
| `cs db reset` | Reset database (destructive) |
| `cs ci-local` | Reproduce CI pipeline locally |
| `cs health` | Check service health |
| `cs dashboard` | Open DevOps dashboard in browser |

---

*This plan focuses on the 80/20 rule: a small set of commands that cover 80% of daily developer needs, rather than trying to replicate every Makefile target.*
