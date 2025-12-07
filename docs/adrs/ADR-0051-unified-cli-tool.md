# ADR-0051: Unified CLI Tool (`./scripts/run`)

**Status**: Implemented | **Date**: 2025-12-03 | **Owners**: Justin Guida

**Related**: [CLI Tool Design Notes](../design-notes/notes/clitool.md), [`scripts/cs_cli.py`](../../scripts/cs_cli.py), [`scripts/runtime_env.py`](../../scripts/runtime_env.py)

## Context

The Contact Suite project had accumulated multiple entry points for common developer tasks, requiring contributors to remember different commands, scripts, and their various options:

| Task                   | Previous Command                              | Problem                                    |
|------------------------|-----------------------------------------------|--------------------------------------------|
| Start dev environment  | `python scripts/dev_stack.py`                 | Long command, required remembering flags   |
| Start backend only     | `mvn spring-boot:run`                         | Need to set env vars manually              |
| Start frontend only    | `cd ui/contact-app && npm run dev`            | Wrong directory, assumes backend running   |
| Start Postgres         | `docker compose -f docker-compose.dev.yml up` | Verbose, easy to forget the file           |
| Run tests              | `mvn test`                                    | Doesn't include frontend or mutation tests |
| Run all quality checks | `make test`                                   | 80+ targets to learn, Unix only            |
| API fuzzing            | `python scripts/api_fuzzing.py --start-app`   | Requires manual app management             |

### Problems Identified

1. **Cognitive Load**: New contributors needed to learn multiple tools and their flags
2. **Cookie Security Bug**: The `dev_stack.py` script was missing `APP_AUTH_COOKIE_SECURE=false`, causing 403 errors on localhost
3. **Environment Drift**: Different scripts configured environment variables inconsistently
4. **Platform Gaps**: Makefile targets only worked on Unix; Windows users had no unified workflow
5. **Guard Rails Missing**: No validation preventing developers from running production configs with insecure settings

## Decision

Create a unified CLI tool (`./scripts/run`) that provides a small set of predictable commands covering 80% of daily developer needs. The CLI wraps Maven, npm, Docker, and existing scripts so new contributors don't need to learn all internal commands.

### Design Principles

1. **Opinionated Defaults**: Sensible defaults that work out of the box (`./scripts/run dev` just works)
2. **Guard Rails Baked In**: Environment variables set automatically per mode (dev vs prod-local)
3. **Reuse Existing Code**: Import from `dev_stack.py` rather than rewriting
4. **Safety Prompts**: Destructive operations require confirmation
5. **Unix & Windows**: Python-based for cross-platform support

### Command Surface

```bash
./scripts/run dev                    # Start development environment
./scripts/run dev --db postgres      # With Postgres instead of H2
./scripts/run prod-local             # Simulate production locally
./scripts/run test                   # Run all quality checks
./scripts/run test --fast            # Skip slow tests
./scripts/run qa-dashboard           # View quality metrics
./scripts/run db start|stop|status   # Database management
./scripts/run ci-local               # Reproduce CI pipeline
./scripts/run health                 # Check service health
./scripts/run dashboard              # Open admin dashboard
```

### Implementation

The CLI is implemented as a Python package using typer:

```
scripts/
├── cs_cli.py           # Main CLI (typer-based)
├── runtime_env.py      # Environment configuration helpers
├── dev_stack.py        # Existing - reused by CLI
├── api_fuzzing.py      # Existing - called by cs test
└── requirements.txt    # CLI dependencies (typer, httpx)

./scripts/run                    # Shell shim at project root
```

### Environment Configuration

The `runtime_env.py` module provides environment builders that validate and configure settings per mode:

| Class                  | Purpose                                        |
|------------------------|------------------------------------------------|
| `DevEnvironment`       | Local HTTP development (insecure cookies OK)   |
| `ProdLocalEnvironment` | Production simulation (requires JWT_SECRET)    |
| `CILocalEnvironment`   | CI reproduction (warns if NVD_API_KEY missing) |

## Consequences

### Positive

- **Onboarding Simplified**: New contributors run `./scripts/run dev` instead of learning 5+ commands
- **Cookie Bug Fixed**: `APP_AUTH_COOKIE_SECURE=false` set automatically for dev mode
- **Consistent Environments**: Each command mode has validated, documented environment settings
- **Safety by Default**: Destructive operations prompt for confirmation
- **Cross-Platform**: Python works on Linux, macOS, and Windows

### Negative

- **Python Dependency**: Requires Python 3.8+ and `pip install -r scripts/requirements.txt`
- **Another Layer**: Advanced users might prefer direct Maven/npm commands
- **Documentation Debt**: Need to update README, ADRs, and other docs to reference CLI

### Trade-offs Accepted

- **Not Replacing Makefile**: The 80+ Makefile targets remain for advanced users; CLI provides simpler subset
- **Not Building Desktop GUI**: PyQt6 dashboard is out of scope for v1 (Phase 5 in roadmap)
- **Admin Backend Missing**: The `./scripts/run dashboard` command opens admin page, but backend needs separate implementation

## Testing

The CLI tool includes:

1. **Integration Tests**: Each command tested against running services
2. **Unit Tests**: Environment builders and configuration logic
3. **Mutation Testing**: PITest coverage on `runtime_env.py`

## Related Decisions

- **ADR-0043**: HttpOnly cookie authentication (CLI respects cookie security settings)
- **ADR-0028**: Frontend-backend build integration (CLI wraps Maven frontend plugin)
- **Design Notes**: [`docs/design-notes/notes/clitool.md`](../design-notes/notes/clitool.md) has full design document

## Migration Guide

| Old Command                                       | New Command               |
|---------------------------------------------------|---------------------------|
| `python scripts/dev_stack.py`                     | `./scripts/run dev`                |
| `python scripts/dev_stack.py --database postgres` | `./scripts/run dev --db postgres`  |
| `docker compose -f docker-compose.dev.yml up -d`  | `./scripts/run db start`           |
| `docker compose -f docker-compose.dev.yml down`   | `./scripts/run db stop`            |
| `mvn test`                                        | `./scripts/run test --unit`        |
| `mvn verify -DskipITs=false`                      | `./scripts/run test --integration` |
| `mvn pitest:mutationCoverage`                     | `./scripts/run test --mutation`    |
| `python scripts/api_fuzzing.py --start-app`       | `./scripts/run test --security`    |
| `python scripts/serve_quality_dashboard.py`       | `./scripts/run qa-dashboard`       |
| `mvn package && java -jar target/*.jar`           | `./scripts/run prod-local`         |
