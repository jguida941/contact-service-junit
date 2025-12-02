# ADR-0042: Docker Containerization Strategy

**Status**: Accepted
**Date**: 2025-12-01
**Owners**: Justin Guida

**Related**: [ADR-0019](ADR-0019-deployment-and-packaging.md),
[ADR-0039](ADR-0039-phase5-security-observability.md),
[Dockerfile](../../Dockerfile),
[docker-compose.yml](../../docker-compose.yml),
[.env.example](../../.env.example)

## Context

The application requires containerized deployment for consistent environments across development, staging, and production. This ADR documents the multi-stage Docker build strategy and the docker-compose stack configuration.

## Decisions

### 1. Multi-Stage Build Strategy

**Dockerfile Structure**:
```
Stage 1: builder (Eclipse Temurin 17 JDK)
  └── Download dependencies
  └── Build JAR
  └── Extract layered JAR

Stage 2: runtime (Eclipse Temurin 17 JRE)
  └── Copy extracted layers
  └── Configure non-root user
  └── Set health check
```

**Base Image Selection**: Eclipse Temurin (formerly AdoptOpenJDK)
- LTS release with predictable support timeline
- Alpine variants for smaller size (`-jammy` suffix uses Ubuntu base for glibc compatibility)
- Consistent behavior across CI and production

### 2. Layer Caching Optimization

Spring Boot's layered JAR format separates the application into four layers:

| Layer | Contents | Change Frequency |
|-------|----------|------------------|
| `dependencies` | External libraries (Maven deps) | Rare |
| `spring-boot-loader` | Boot loader classes | Rare |
| `snapshot-dependencies` | SNAPSHOT dependencies | Occasional |
| `application` | Our compiled code | Every build |

**Extraction Command**:
```bash
java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted
```

**Copy Order** (least to most frequently changing):
```dockerfile
COPY --from=builder /build/target/extracted/dependencies/ ./
COPY --from=builder /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/ ./
```

**Cache Benefit**: Code changes only invalidate the `application` layer; dependencies remain cached.

### 3. Security Hardening

| Measure | Implementation |
|---------|----------------|
| Non-root user | `useradd -r -u 1001 -s /bin/false appuser` |
| Minimal runtime | JRE image (no compiler, no build tools) |
| No shell access | User created with `/bin/false` shell |
| File ownership | `--chown=appuser:appuser` on COPY |

### 4. JVM Configuration

**Default JAVA_OPTS**:
```
-XX:+UseContainerSupport          # Respect cgroup memory limits
-XX:MaxRAMPercentage=75.0         # Use 75% of container memory for heap
-XX:+ExitOnOutOfMemoryError       # Kill process on OOM (let orchestrator restart)
-Dspring.profiles.active=prod     # Production profile by default
```

**Container-Aware Memory**: `UseContainerSupport` (default in JDK 10+) reads cgroup limits instead of host memory. `MaxRAMPercentage=75.0` leaves headroom for metaspace and native memory.

### 5. Health Check

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `interval` | 30s | Balance between responsiveness and overhead |
| `timeout` | 3s | Fast detection of unresponsive services |
| `start-period` | 60s | Allow Spring Boot startup (JIT, bean init) |
| `retries` | 3 | Avoid false positives from transient issues |

### 6. Docker Compose Stack

**Services**:
| Service | Image | Purpose |
|---------|-------|---------|
| `postgres` | `postgres:16-alpine` | Primary database |
| `app` | Local build | Spring Boot application |
| `pgadmin` | `dpage/pgadmin4` | Optional DB management UI |

**Network Isolation**: All services on isolated bridge network (`contactapp-network`). Database port NOT exposed to host.

**Environment Variables** (via `.env` file):
| Variable | Required | Default |
|----------|----------|---------|
| `POSTGRES_PASSWORD` | Yes | (none) |
| `JWT_SECRET` | Yes | (none) |
| `POSTGRES_DB` | No | `contactapp` |
| `POSTGRES_USER` | No | `contactapp` |
| `ENVIRONMENT` | No | `docker` |

**Resource Limits**:
```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 1G
    reservations:
      cpus: '0.5'
      memory: 512M
```

### 7. Build Optimizations

**Skipped in Docker Build** (run in CI instead):
- `-DskipTests` - Tests run in dedicated CI job
- `-Dpit.skip=true` - Mutation testing too slow
- `-Dspotbugs.skip=true` - Static analysis in CI
- `-Ddependency.check.skip=true` - OWASP scan in CI
- `-Dcheckstyle.skip=true` - Style checks in CI

**Dependency Caching**:
```dockerfile
# Copy pom.xml first (changes rarely)
COPY mvnw pom.xml ./

# Download all dependencies in separate layer
RUN ./mvnw dependency:go-offline -B
```

## Usage

### Development

```bash
# Build and run entire stack
docker compose up --build

# View logs
docker compose logs -f app

# Stop without data loss
docker compose down

# Stop with data removal
docker compose down -v
```

### Production Deployment

```bash
# Set required environment variables
export POSTGRES_PASSWORD=<secure-password>
export JWT_SECRET=<base64-256-bit-secret>

# Build optimized image
docker build -t contactapp:latest .

# Run with external orchestration (K8s, ECS, etc.)
```

## File Structure

```
/
├── Dockerfile                 # Multi-stage production build
├── docker-compose.yml         # Full stack (postgres + app + pgadmin)
├── docker-compose.dev.yml     # Development database only
├── .env.example               # Template for required variables
└── .dockerignore              # Exclude build artifacts, node_modules
```

## Consequences

### Positive
- ~200MB final image (vs ~700MB with full JDK)
- Fast rebuilds via layer caching
- Consistent environments across dev/staging/prod
- Security hardening out of the box

### Trade-offs
- Multi-stage builds are more complex than single-stage
- Layer extraction adds build time (~5s)
- pgAdmin included (should be removed for production)

## Alternatives Considered

### Base Images
- **OpenJDK official**: Rejected; Temurin has better container support and updates
- **GraalVM native image**: Deferred; requires testing for compatibility
- **Distroless**: Considered; minimal attack surface but harder debugging

### Compose vs Kubernetes
- **Kubernetes**: Deferred to Phase 6; compose sufficient for current scale
- **Docker Swarm**: Rejected; less ecosystem support than K8s

### Secrets Management
- **Docker secrets**: Considered; adds complexity for single-host deployments
- **HashiCorp Vault**: Deferred; environment variables sufficient for Phase 5
