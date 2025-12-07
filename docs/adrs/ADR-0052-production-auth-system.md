# ADR-0052: Production-Grade Secure Authentication System

**Status:** Implemented | **Date:** 2025-12-03 (Implemented 2025-12-06) | **Owners:** Development Team

**Related:** ADR-0018 (Auth Model), ADR-0038 (Auth Implementation), ADR-0043 (HttpOnly Cookies)

**Code Review Reference:** [CODE_REVIEW.md](../logs/CODE_REVIEW.md) (2025-12-03)

---

## Code Review Findings Integration

This ADR directly addresses several issues identified in the code review conducted on 2025-12-03:

### Issues This ADR Resolves

| Finding          | Severity | Description                                                                               | How This ADR Fixes It                                                                                            |
|------------------|----------|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **SECURITY-002** | Medium   | 403 Forbidden auto-logout too aggressive; 403 treated same as 401                         | Phase 5 adds proper `AuthenticationEntryPoint` returning 401 for unauthenticated, 403 only for permission denial |
| **AUTH-R01**     | Medium   | Token refresh race condition after page reload; `expiresIn` not adjusted for elapsed time | Phase 3-4 implements proper refresh token flow; frontend stores actual expiration timestamp                      |
| **SECURITY-001** | Medium   | Default JWT secret predictable in non-production profiles                                 | Addressed by requiring `JWT_SECRET` env var; dev profile can generate random secret at startup                   |
| **SECURITY-003** | Low      | Hardcoded dev JWT secret in `runtime_env.py`                                              | Replace with `secrets.token_hex(32)` runtime generation                                                          |

### Related Issues to Address Alongside

| Finding | Severity | Description | Recommendation |
|---------|----------|-------------|----------------|
| **PERF-001** | Medium | `getAppointmentsByProjectId` filters in-memory instead of database | Add `findByProjectId(projectId, user)` to store interface |
| **AUTH-R02** | Low | CSRF token fetch failure proceeds silently | Add logging/throw when CSRF acquisition fails |

### New Test Coverage Required

The code review noted missing test coverage for archive/unarchive endpoints. This ADR adds:
- ~15-20 new unit tests for `TokenFingerprintService` and `RefreshTokenService`
- ~10 integration tests for refresh/logout flows
- ~5 CLI tests for `setup-ssl` command

Combined with the ~10-15 archive/unarchive tests flagged in the review, total new test count: **~35-50 tests**.

---

## Problem

- No HTTPS - cookies transmitted insecurely
- No token fingerprinting - stolen tokens can be reused
- No refresh token mechanism - users must re-login constantly
- Backend returns 403 instead of 401 for expired tokens
- No easy way to set up SSL for development

---

## The Complete Secure Solution

Based on OWASP best practices and industry standards:

1. **HTTPS everywhere** - self-signed cert for dev, real cert for prod
2. **Token fingerprinting** - SHA256 hash prevents stolen token reuse (OWASP sidejacking prevention)
3. **Database-backed refresh tokens** - with rotation on each use
4. **Proper cookie flags** - `HttpOnly; Secure; SameSite=Lax; __Secure-` prefix
   > **Cookie Policy:** `SameSite=Lax` is the default for all auth-related cookies (access, refresh, fingerprint). CSRF tokens provide a second layer of defense.
5. **CLI setup command** - `./cs setup-ssl` generates certs automatically
6. **Token use separation** - `token_use=session` (browser) vs `token_use=api` (programmatic):
   - Session tokens carry `fph` and are enforced with fingerprint cookies; default for legacy tokens when claim absent.
   - API tokens omit `fph`, are header-only, and are rejected by the filter if an `fph` claim is present.
   - Issuance: `/api/auth/login|register|refresh` → session tokens with cookies; `/api/auth/api-token` → header-only API token (no cookies).

### Production TLS Strategy

**Development:** Self-signed cert via `./cs setup-ssl` (stays local, gitignored)

**Production:** TLS terminated at edge (recommended industry pattern)

| Component | Responsibility |
|-----------|---------------|
| **Edge (Nginx/Caddy/ALB/Cloudflare)** | Obtains & auto-renews Let's Encrypt certs, enforces TLS 1.2+, redirects HTTP→HTTPS |
| **Spring Boot** | Runs plain HTTP behind proxy, trusts `X-Forwarded-Proto` for cookie security |

**Production config (`application-prod.yml`):**
```yaml
server:
  ssl:
    enabled: false  # TLS handled by edge
  forward-headers-strategy: framework
  tomcat:
    remote-ip-header: X-Forwarded-For
    protocol-header: X-Forwarded-Proto
```

**Security requirements:**
- No private keys or keystores in git
- Keystore readable only by application user
- Renewal must be automated and monitored (alert when expiry < 15 days)
- Use Let's Encrypt staging for non-prod (avoids rate limits)
- **Production MUST use `--spring.profiles.active=prod`** which enforces `app.auth.cookie.secure=true`

**Configuration philosophy:**
- **Dev defaults are HTTP-friendly** (`SSL_ENABLED=false`) for easy setup
- **Prod profile enforces HTTPS** via `application-prod.yml`
- This is the [industry standard pattern](https://www.thomasvitale.com/https-spring-boot-ssl-certificate/) - don't break dev to enforce prod security

**Alternative (Spring Boot terminates TLS directly):**
```bash
# Certbot renewal hook converts PEM → PKCS12
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out /opt/contact-suite/keystore.p12 \
  -name tomcat -password pass:${SSL_KEYSTORE_PASSWORD}
```

### Sources

- https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
- https://www.codejava.net/frameworks/spring-boot/configure-https-with-self-signed-certificate
- https://www.bezkoder.com/spring-boot/refresh-token-jwt/

---

## Phase 0: Critical Pre-Implementation Fixes (MUST DO FIRST)

> **Audit Finding (2025-12-03):** A 6-agent parallel security audit identified 4 CRITICAL issues that must be fixed before proceeding with ADR-0052 phases. These establish a sane baseline for the security work.

### 0.1 Protect User.password from JSON Leak (CRITICAL)

**File:** `src/main/java/contactapp/security/User.java`

**Issue:** The `password` field (BCrypt hash) lacks `@JsonIgnore`. If the entity is ever serialized to JSON (logs, error responses, API), the hash could leak.

```java
@JsonIgnore  // ADD THIS - defense in depth
@Column(nullable = false, length = Validation.MAX_PASSWORD_LENGTH)
private String password;

// Also on getter for double protection
@Override
@JsonIgnore
public String getPassword() {
    return password;
}
```

**Long-term:** Consider DTOs that never contain password fields, with explicit mapping from entities.

### 0.2 Fix Frontend 403 Auto-Logout (CRITICAL)

**File:** `ui/contact-app/src/lib/api.ts`

**Issue:** Current code treats 403 same as 401, logging out users on permission denied.

**Current (WRONG):**
```typescript
if (response.status === 401 || response.status === 403) {
  tokenStorage.clear();
  // ... redirect to login
}
```

**Fixed:**
```typescript
if (response.status === 401) {
  // 401 = Unauthenticated - attempt refresh once, then logout
  const refreshed = await attemptTokenRefresh();
  if (!refreshed) {
    tokenStorage.clear();
    queryClient.clear();
    window.location.href = '/login?reason=sessionExpired';
  }
} else if (response.status === 403) {
  // 403 = Forbidden (authenticated but unauthorized)
  // Do NOT logout - show permission denied, keep session
  throw new ApiError('Forbidden', 403, { message: 'Access denied' });
}
```

### 0.3 Add AccessDeniedHandler to SecurityConfig (CRITICAL)

**File:** `src/main/java/contactapp/security/SecurityConfig.java`

**Issue:** No custom `AccessDeniedHandler` means inconsistent 403 responses.

```java
http
    .exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        })
        .accessDeniedHandler((request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"error\":\"Forbidden\",\"message\":\"Access is denied\"}");
        })
    )
```

### 0.4 Add Clock Skew Tolerance to JWT Parser (CRITICAL)

**File:** `src/main/java/contactapp/security/JwtService.java`

**Issue:** No clock skew tolerance causes random "token invalid" errors when servers have slight time drift.

```java
private Claims extractAllClaims(final String token) {
    return Jwts.parser()
            .verifyWith(getSignInKey())
            .clockSkewSeconds(60)  // ADD THIS - 1 minute tolerance
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

### 0.5 Migrate User.id from Long to UUID (CRITICAL)

**Issue:** Sequential Long IDs enable user count enumeration attacks.

**Decision:** Migrate to UUID via V15 data-preserving migration. This approach safely handles existing data in any environment (dev, staging, production).

#### Database Migration Strategy

The migration uses a **data-preserving pattern** that works with existing data:

1. **Add** new UUID column alongside existing BIGINT id
2. **Backfill** UUIDs for all existing rows
3. **Drop** foreign key constraints referencing users.id
4. **Alter** FK columns in child tables from BIGINT to UUID
5. **Backfill** FK columns via JOIN to link to new UUIDs
6. **Drop** old BIGINT primary key
7. **Promote** UUID column to primary key
8. **Recreate** foreign key constraints and indexes

#### PostgreSQL Migration: `V15__users_uuid_id.sql`

```sql
-- V15: Migrate users.id from BIGSERIAL to UUID (data-preserving)
-- ADR-0052 Phase 0.5: UUID migration to prevent user enumeration attacks

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Step 1: Add new UUID column to users table
ALTER TABLE users ADD COLUMN new_id UUID DEFAULT gen_random_uuid();

-- Step 2: Backfill UUIDs for existing rows
UPDATE users SET new_id = gen_random_uuid() WHERE new_id IS NULL;

-- Step 3: Add new UUID columns to child tables
ALTER TABLE contacts ADD COLUMN new_user_id UUID;
ALTER TABLE tasks ADD COLUMN new_user_id UUID;
ALTER TABLE tasks ADD COLUMN new_assignee_id UUID;
ALTER TABLE appointments ADD COLUMN new_user_id UUID;
ALTER TABLE projects ADD COLUMN new_user_id UUID;

-- Step 4: Backfill FK columns via JOIN
UPDATE contacts c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;
UPDATE tasks t SET new_user_id = u.new_id FROM users u WHERE t.user_id = u.id;
UPDATE tasks t SET new_assignee_id = u.new_id FROM users u WHERE t.assignee_id = u.id;
UPDATE appointments a SET new_user_id = u.new_id FROM users u WHERE a.user_id = u.id;
UPDATE projects p SET new_user_id = u.new_id FROM users u WHERE p.user_id = u.id;

-- Step 5: Drop existing foreign key constraints
ALTER TABLE contacts DROP CONSTRAINT IF EXISTS fk_contacts_user_id;
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS fk_tasks_user_id;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_user_id;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS fk_projects_user_id;

-- Step 6: Drop existing indexes on user_id columns
DROP INDEX IF EXISTS idx_contacts_user_id;
DROP INDEX IF EXISTS idx_tasks_user_id;
DROP INDEX IF EXISTS idx_tasks_assignee_id;
DROP INDEX IF EXISTS idx_appointments_user_id;
DROP INDEX IF EXISTS idx_projects_user_id;
DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_users_email;

-- Step 7: Drop old columns and rename new columns
ALTER TABLE contacts DROP COLUMN user_id;
ALTER TABLE contacts RENAME COLUMN new_user_id TO user_id;
ALTER TABLE contacts ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE tasks DROP COLUMN user_id;
ALTER TABLE tasks RENAME COLUMN new_user_id TO user_id;
ALTER TABLE tasks ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE tasks DROP COLUMN assignee_id;
ALTER TABLE tasks RENAME COLUMN new_assignee_id TO assignee_id;
-- assignee_id remains nullable

ALTER TABLE appointments DROP COLUMN user_id;
ALTER TABLE appointments RENAME COLUMN new_user_id TO user_id;
ALTER TABLE appointments ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE projects DROP COLUMN user_id;
ALTER TABLE projects RENAME COLUMN new_user_id TO user_id;
ALTER TABLE projects ALTER COLUMN user_id SET NOT NULL;

-- Step 8: Drop old users.id PK and promote new_id
ALTER TABLE users DROP CONSTRAINT users_pkey;
DROP SEQUENCE IF EXISTS users_id_seq CASCADE;
ALTER TABLE users DROP COLUMN id;
ALTER TABLE users RENAME COLUMN new_id TO id;
ALTER TABLE users ALTER COLUMN id SET NOT NULL;
ALTER TABLE users ADD PRIMARY KEY (id);

-- Step 9: Recreate foreign key constraints
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
-- assignee_id references users but should SET NULL when user is deleted (nullable column)
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assignee_id
    FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL;

-- Step 10: Recreate indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_contacts_user_id ON contacts(user_id);
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_appointments_user_id ON appointments(user_id);
CREATE INDEX idx_projects_user_id ON projects(user_id);

-- Step 11: Recreate unique constraint on projects
ALTER TABLE projects DROP CONSTRAINT IF EXISTS uq_projects_project_id_user_id;
ALTER TABLE projects ADD CONSTRAINT uq_projects_project_id_user_id UNIQUE (project_id, user_id);
```

#### H2 Migration: `V15__users_uuid_id.sql` (h2 folder)

H2 uses `RANDOM_UUID()` instead of `gen_random_uuid()` and has slightly different syntax for UPDATE...FROM. See `src/main/resources/db/migration/h2/V15__users_uuid_id.sql` for H2-specific implementation.

#### Entity Update

**File:** `src/main/java/contactapp/security/User.java`

```java
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    private UUID id;  // Changed from Long, generated via @PrePersist

    // ... rest unchanged

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();  // Portable across all databases
        }
        // ... timestamp initialization
    }

    public UUID getId() { return id; }
}
```

#### Impact on ADR-0052

- RefreshToken `user_id` column: `UUID NOT NULL REFERENCES users(id)`
- RefreshTokenService: `createRefreshToken(UUID userId)`
- RefreshTokenRepository: `revokeAllByUserId(@Param("userId") UUID userId)`
- All tests using `Long userId = 1L` → `UUID userId = UUID.randomUUID()`

---

## Phase 0.5: JWT Claim Hardening (OWASP Compliance)

### Add Issuer and Audience Claims

**File:** `src/main/java/contactapp/security/JwtService.java`

Per [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html), tokens should include and validate `iss` and `aud` claims.

```java
private static final String ISSUER = "contact-service";
private static final String AUDIENCE = "contact-service-api";

public String generateToken(UserDetails userDetails, String fingerprintHash) {
    return Jwts.builder()
        .issuer(ISSUER)                           // ADD
        .audience().add(AUDIENCE).and()           // ADD
        .subject(userDetails.getUsername())
        .claim("fingerprint", fingerprintHash)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();
}

private Claims extractAllClaims(final String token) {
    return Jwts.parser()
            .verifyWith(getSignInKey())
            .requireIssuer(ISSUER)                // ADD - reject wrong issuer
            .requireAudience(AUDIENCE)            // ADD - reject wrong audience
            .clockSkewSeconds(60)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

### JTI (Token ID) - Optional for Access Tokens

The refresh token already has a unique identifier (`token` column in DB). For access tokens:
- JTI is optional since they're short-lived (15 min) and fingerprint-bound
- Can add for observability/logging if needed:

```java
.id(UUID.randomUUID().toString())  // Optional - for logging/traceability
```

---

## Phase 1: HTTPS Setup with Self-Signed Certificate

### 1.1 CLI Command: `./cs setup-ssl`

**File:** `scripts/cs_cli.py` - Add new command

```python
@app.command("setup-ssl")
def setup_ssl():
    """Generate self-signed SSL certificate for HTTPS development."""
    keystore_path = ROOT / "src" / "main" / "resources" / "local-ssl.p12"
    cert_path = ROOT / "certs" / "local-cert.crt"

    # Generate keystore
    subprocess.run([
        "keytool", "-genkeypair",
        "-alias", "local_ssl",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-storetype", "PKCS12",
        "-keystore", str(keystore_path),
        "-validity", "365",
        "-storepass", "changeit",
        "-keypass", "changeit",
        "-dname", "CN=localhost,OU=Dev,O=ContactApp,L=Local,ST=Dev,C=US",
        "-ext", "san=dns:localhost,ip:127.0.0.1"
    ])

    # Export certificate for browser trust
    (ROOT / "certs").mkdir(exist_ok=True)
    subprocess.run([
        "keytool", "-export",
        "-keystore", str(keystore_path),
        "-alias", "local_ssl",
        "-file", str(cert_path),
        "-storepass", "changeit"
    ])

    console.print("[green]SSL certificate generated![/green]")
    console.print(f"Keystore: {keystore_path}")
    console.print(f"Certificate: {cert_path}")
    console.print("\n[yellow]To trust in your browser:[/yellow]")
    console.print("  macOS: open certs/local-cert.crt → Add to Keychain → Trust")
    console.print("  Windows: certutil -addstore root certs\\local-cert.crt")
```

### 1.2 Spring Boot SSL Configuration

**File:** `src/main/resources/application.yml` - Add SSL config

```yaml
server:
  port: 8443  # HTTPS port
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: classpath:local-ssl.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: local_ssl
```

**Dev profile override:**

```yaml
---
spring:
  config:
    activate:
      on-profile: dev
server:
  ssl:
    enabled: true
```

---

## Phase 2: Token Fingerprinting (OWASP Sidejacking Prevention)

### 2.1 Fingerprint Service

**File:** `src/main/java/contactapp/security/TokenFingerprintService.java`

```java
@Service
public class TokenFingerprintService {

    // __Secure- prefix REQUIRES Secure=true and HTTPS, otherwise browsers reject it
    private static final String SECURE_FINGERPRINT_COOKIE = "__Secure-Fgp";
    private static final String DEV_FINGERPRINT_COOKIE = "Fgp";  // For HTTP dev (no prefix)
    private static final int FINGERPRINT_LENGTH = 50;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a random fingerprint for token binding.
     * Returns the raw fingerprint (for cookie) and its SHA256 hash (for JWT).
     */
    public FingerprintPair generateFingerprint() {
        byte[] randomBytes = new byte[FINGERPRINT_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String rawFingerprint = HexFormat.of().formatHex(randomBytes);
        String hashedFingerprint = hashFingerprint(rawFingerprint);
        return new FingerprintPair(rawFingerprint, hashedFingerprint);
    }

    /**
     * Hash fingerprint using SHA-256.
     */
    public String hashFingerprint(String fingerprint) {
      try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Get the correct cookie name based on secure flag.
     * __Secure- prefix only works with Secure=true over HTTPS.
     */
    public String getCookieName(boolean secure) {
        return secure ? SECURE_FINGERPRINT_COOKIE : DEV_FINGERPRINT_COOKIE;
    }

    /**
     * Create hardened fingerprint cookie per OWASP guidelines.
     * Uses __Secure-Fgp for HTTPS, plain Fgp for HTTP dev.
     */
    public ResponseCookie createFingerprintCookie(String fingerprint, boolean secure) {
        String cookieName = getCookieName(secure);
        return ResponseCookie.from(cookieName, fingerprint)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")  // Lax with CSRF token protection
            .path("/")
            .maxAge(Duration.ofMinutes(15)) // Match access token expiry (900000ms = 15min)
            .build();
    }

    /**
     * Extract fingerprint from request cookie.
     * Checks both __Secure-Fgp (HTTPS) and Fgp (HTTP dev) cookie names.
     */
    public Optional<String> extractFingerprint(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(c -> SECURE_FINGERPRINT_COOKIE.equals(c.getName())
                      || DEV_FINGERPRINT_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }

    /**
     * Verify fingerprint matches JWT claim.
     */
    public boolean verifyFingerprint(String cookieFingerprint, String jwtFingerprintHash) {
        if (cookieFingerprint == null || jwtFingerprintHash == null) return false;
        String computedHash = hashFingerprint(cookieFingerprint);
        return MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            jwtFingerprintHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record FingerprintPair(String raw, String hashed) {}
}
```

### 2.2 Update JwtService to Include Fingerprint

**File:** `src/main/java/contactapp/security/JwtService.java` - Modify

```java
// Add fingerprint claim when generating token
public String generateToken(UserDetails userDetails, String fingerprintHash) {
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("fingerprint", fingerprintHash)  // Add fingerprint hash
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();
}

// Extract fingerprint claim
public String extractFingerprintHash(String token) {
    return extractClaim(token, claims -> claims.get("fingerprint", String.class));
}
```

### 2.3 Update JwtAuthenticationFilter

**File:** `src/main/java/contactapp/security/JwtAuthenticationFilter.java` - Modify

```java
@Override
protected void doFilterInternal(...) {
    final String jwt = extractJwtFromCookie(request).orElse(null);
    if (jwt == null) {
        filterChain.doFilter(request, response);
        return;
    }

    try {
        // Verify fingerprint BEFORE processing token
        String jwtFingerprintHash = jwtService.extractFingerprintHash(jwt);
        Optional<String> cookieFingerprint = fingerprintService.extractFingerprint(request);

        if (jwtFingerprintHash != null &&
            !fingerprintService.verifyFingerprint(cookieFingerprint.orElse(null), jwtFingerprintHash)) {
            logger.warn("Token fingerprint mismatch - possible token theft");
            filterChain.doFilter(request, response);
            return; // Reject - fingerprint doesn't match
        }

        // Continue with existing validation...
        final String username = jwtService.extractUsername(jwt);
        // ... rest of existing code
    } catch (Exception e) {
        logger.debug("JWT validation failed");
    }

    filterChain.doFilter(request, response);
}
```

---

## Phase 3: Database-Backed Refresh Tokens

### 3.1 RefreshToken Entity

**File:** `src/main/java/contactapp/security/RefreshToken.java`

**⚠️ POST-AUDIT UPDATE:** Per Phase 0.5, `User.id` is now `UUID`. RefreshToken keeps `Long id` for its own PK, but the `user` FK references `UUID`. No Lombok - use manual getters/setters following existing entity patterns.

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;  // RefreshToken's PK is UUID (consistent with User.id)

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JPA entity returns User reference for ORM relationship")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Protected no-arg constructor for JPA
    protected RefreshToken() {}

    // Full constructor
    public RefreshToken(User user, String token, Instant expiryDate) {
        this.user = user;
        this.token = token;
        this.expiryDate = expiryDate;
        this.revoked = false;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // Manual getters/setters (no Lombok - matches existing entity pattern)
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getToken() { return token; }
    public Instant getExpiryDate() { return expiryDate; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }
}
```

### 3.2 Flyway Migration

**File:** `src/main/resources/db/migration/common/V16__create_refresh_tokens.sql`

**⚠️ POST-AUDIT UPDATE:** Per Phase 0.5, `User.id` is now `UUID`. Migration uses UUID for `user_id` FK.

```sql
-- RefreshToken table for database-backed refresh tokens
-- User.id is UUID (per Phase 0.5 migration)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expiry_date);
```

### 3.3 RefreshTokenService

**File:** `src/main/java/contactapp/security/RefreshTokenService.java`

```java
@Service
@Transactional
public class RefreshTokenService {

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private long refreshTokenDurationMs;

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public RefreshToken createRefreshToken(UUID userId) {
        // Revoke existing tokens (single active session)
        repository.revokeAllByUserId(userId);

        // Use constructor - no Lombok/builder (matches existing entity pattern)
        RefreshToken token = new RefreshToken(
            userRepository.getReferenceById(userId),
            UUID.randomUUID().toString(),
            Instant.now().plusMillis(refreshTokenDurationMs)
        );

        return repository.save(token);
    }

    public Optional<RefreshToken> findValidToken(String token) {
        return repository.findByTokenAndRevokedFalse(token)
            .filter(t -> t.getExpiryDate().isAfter(Instant.now()));
    }

    public void revokeToken(String token) {
        repository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            repository.save(t);
        });
    }

    @Scheduled(cron = "0 0 * * * *") // Hourly cleanup
    public void cleanupExpiredTokens() {
        repository.deleteAllExpired(Instant.now());  // Delete ALL expired, not just revoked
    }
}
```

---

## Phase 4: Update AuthController

**File:** `src/main/java/contactapp/api/AuthController.java`

```java
public static final String REFRESH_COOKIE_NAME = "refresh_token";

@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
    // Authenticate
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    User user = (User) auth.getPrincipal();

    // Generate fingerprint
    var fingerprint = fingerprintService.generateFingerprint();

    // Generate access token with fingerprint hash
    String accessToken = jwtService.generateToken(user, fingerprint.hashed());

    // Create refresh token
    RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

    // Build cookies
    ResponseCookie accessCookie = ResponseCookie.from(AUTH_COOKIE_NAME, accessToken)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")  // Lax with CSRF token protection
        .path("/")
        .maxAge(Duration.ofMillis(jwtService.getExpiration()))
        .build();

    ResponseCookie fingerprintCookie = fingerprintService.createFingerprintCookie(
        fingerprint.raw(), cookieSecure);

    ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken.getToken())
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")  // Lax with CSRF token protection
        .path("/api/auth/refresh")
        .maxAge(Duration.ofDays(7))
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
        .header(HttpHeaders.SET_COOKIE, fingerprintCookie.toString())
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(new AuthResponse(user.getUsername(), user.getEmail(),
            user.getRole().name(), jwtService.getExpiration()));
}

@PostMapping("/refresh")
public ResponseEntity<?> refresh(HttpServletRequest request) {
    String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);

    return refreshTokenService.findValidToken(refreshToken)
        .map(token -> {
            User user = token.getUser();

            // Rotate: revoke old, create new
            refreshTokenService.revokeToken(refreshToken);
            RefreshToken newRefresh = refreshTokenService.createRefreshToken(user.getId());

            // Generate new fingerprint and access token
            var fingerprint = fingerprintService.generateFingerprint();
            String newAccess = jwtService.generateToken(user, fingerprint.hashed());

            // Return new cookies
            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createAccessCookie(newAccess).toString())
                .header(HttpHeaders.SET_COOKIE, fingerprintService.createFingerprintCookie(
                    fingerprint.raw(), cookieSecure).toString())
                .header(HttpHeaders.SET_COOKIE, createRefreshCookie(newRefresh.getToken()).toString())
                .body(new AuthResponse(user, jwtService.getExpiration()));
        })
        .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Invalid or expired refresh token")));
}

@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
    String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
    if (refreshToken != null) {
        refreshTokenService.revokeToken(refreshToken);
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearCookie(AUTH_COOKIE_NAME, "/").toString())
        .header(HttpHeaders.SET_COOKIE, clearCookie("__Secure-Fgp", "/").toString())
        .header(HttpHeaders.SET_COOKIE, clearCookie("Fgp", "/").toString())  // Clear both cookie names
        .header(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_COOKIE_NAME, "/api/auth/refresh").toString())
        .build();
}
```

---

## Phase 5: SecurityConfig - Add 401 Entry Point

**File:** `src/main/java/contactapp/security/SecurityConfig.java`

```java
http
    .exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        })
    )
```

---

## Phase 6: Configuration

**File:** `src/main/resources/application.yml`

```yaml
jwt:
  secret: ${JWT_SECRET:}       # No default - required in non-dev; dev uses runtime_env.py
  expiration: 900000           # 15 minutes access token
  refresh-expiration: 604800000  # 7 days refresh token

server:
  port: ${SERVER_PORT:8443}
  ssl:
    enabled: ${SSL_ENABLED:false}  # Default off; dev profile enables it
    key-store: classpath:local-ssl.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: local_ssl
```

> **Note:** In dev, `runtime_env.py` injects a random `JWT_SECRET` via `secrets.token_hex(32)`. In prod, `JWT_SECRET` must be set externally or the application fails to start.

---

## Files Summary

### Phase 0 (Critical Pre-Implementation Fixes)

| File                                                                    | Action                                                      |
|-------------------------------------------------------------------------|-------------------------------------------------------------|
| `src/main/java/contactapp/security/User.java`                           | MODIFY (@JsonIgnore on password, change id Long→UUID)       |
| `src/main/java/contactapp/security/SecurityConfig.java`                 | MODIFY (add AccessDeniedHandler for 403)                    |
| `src/main/java/contactapp/security/JwtService.java`                     | MODIFY (clock skew tolerance, issuer/audience claims)       |
| `ui/contact-app/src/lib/api.ts`                                         | MODIFY (fix 403 handling - don't auto-logout)               |
| `src/main/resources/db/migration/common/V15__users_uuid_id.sql`         | CREATE (migrate users.id from BIGINT to UUID)               |

### Main Implementation Phases (A-C)

| File                                                                    | Action                             |
|-------------------------------------------------------------------------|------------------------------------|
| `scripts/cs_cli.py`                                                     | ADD setup-ssl command              |
| `src/main/java/contactapp/security/TokenFingerprintService.java`        | CREATE                             |
| `src/main/java/contactapp/security/RefreshToken.java`                   | CREATE                             |
| `src/main/java/contactapp/security/RefreshTokenRepository.java`         | CREATE                             |
| `src/main/java/contactapp/security/RefreshTokenService.java`            | CREATE                             |
| `src/main/resources/db/migration/common/V16__create_refresh_tokens.sql` | CREATE (after V15 UUID migration)  |
| `src/main/java/contactapp/security/JwtService.java`                     | MODIFY (add fingerprint claim)     |
| `src/main/java/contactapp/security/JwtAuthenticationFilter.java`        | MODIFY (verify fingerprint)        |
| `src/main/java/contactapp/api/AuthController.java`                      | MODIFY (refresh endpoint, cookies) |
| `src/main/java/contactapp/security/SecurityConfig.java`                 | MODIFY (401 entry point)           |
| `src/main/resources/application.yml`                                    | MODIFY (SSL, refresh config)       |
| `ui/contact-app/src/lib/api.ts`                                         | MODIFY (retry after refresh)       |

---

## User Setup Flow

```bash
# One-time setup
./cs setup-ssl                    # Generate certificate
# Import certs/local-cert.crt to browser/OS trust store

# Start development (now uses HTTPS)
./cs dev                          # https://localhost:8443
```

---

## Phased Rollout (Recommended)

To reduce risk and complexity, implement in phases:

### Phase A: HTTPS + 401 Entry Point

- Add `./cs setup-ssl` command
- Configure Spring Boot SSL
- Add AuthenticationEntryPoint for proper 401 responses
- Keep current single JWT cookie (no refresh yet)
- **Testable checkpoint:** HTTPS works, 401 returned for expired tokens

### Phase B: Refresh Token System

- Create RefreshToken entity and migration
- Create RefreshTokenService with rotation
- Update AuthController with `/refresh` endpoint
- Update frontend `api.ts` to call refresh
- **Testable checkpoint:** Users can stay logged in for 7 days

### Phase C: Token Fingerprinting

- Create TokenFingerprintService
- Add fingerprint claim to JWT
- Add fingerprint verification to filter
- **Testable checkpoint:** Stolen tokens rejected without matching cookie

---

## CI/CD Considerations

### Requirements

- `keytool` must be on PATH (comes with JDK)
- For CI, skip SSL setup or use pre-generated test certs
- GitHub Actions runners have JDK, so keytool is available

### Test Environment

```yaml
# In java-ci.yml, tests run with SSL disabled
env:
  SSL_ENABLED: false
  SPRING_PROFILES_ACTIVE: test
```

### Production Deployment

- Do NOT ship `local-ssl.p12` to production
- Either:
  - Terminate TLS at reverse proxy/ingress (common)
  - Use real cert from Let's Encrypt/CA
- Set `SSL_ENABLED=false` if TLS terminated at proxy

---

## Security Checklist (Implementation Tracking)

> **Note:** All items have been implemented across Batches 1-9 (2025-12-03).

- [x] HTTPS with TLS 1.2+ (self-signed for dev, real cert for prod) ✅ Batch 3
- [x] Token fingerprinting (OWASP sidejacking prevention) ✅ Batch 4-5
- [x] Short-lived access tokens (30 min prod, 8 hr dev) ✅ Batch 1
- [x] Long-lived refresh tokens (7 days, database-backed) ✅ Batch 6-7
- [x] Token rotation on refresh ✅ Batch 7
- [x] Hardened cookies: `HttpOnly; Secure; SameSite=Lax` (with CSRF token as second layer) ✅ Batch 4
- [x] `__Secure-Fgp` for HTTPS, `Fgp` for HTTP dev (cookie prefix fix) ✅ Batch 4
- [x] 401 for unauthenticated, 403 for unauthorized ✅ Batch 1
- [x] Revoke refresh token on logout ✅ Batch 7
- [x] Scheduled cleanup of expired tokens ✅ Batch 6-7
- [x] Clear dev vs prod separation (`changeit` password is dev-only) ✅ Batch 3

---

## Phase 7: Testing

### 7.1 TokenFingerprintService Tests

**File:** `src/test/java/contactapp/security/TokenFingerprintServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class TokenFingerprintServiceTest {

    private TokenFingerprintService service;

    @BeforeEach
    void setUp() {
        service = new TokenFingerprintService();
    }

    @Test
    void generateFingerprint_returnsUniqueValues() {
        var fp1 = service.generateFingerprint();
        var fp2 = service.generateFingerprint();

        assertThat(fp1.raw()).isNotEqualTo(fp2.raw());
        assertThat(fp1.hashed()).isNotEqualTo(fp2.hashed());
    }

    @Test
    void generateFingerprint_rawIs100HexChars() {
        var fp = service.generateFingerprint();
        assertThat(fp.raw()).hasSize(100); // 50 bytes = 100 hex chars
        assertThat(fp.raw()).matches("[0-9a-f]+");
    }

    @Test
    void hashFingerprint_producesSha256() {
        var fp = service.generateFingerprint();
        assertThat(fp.hashed()).hasSize(64); // SHA-256 = 64 hex chars
    }

    @Test
    void verifyFingerprint_matchesWhenCorrect() {
        var fp = service.generateFingerprint();
        assertThat(service.verifyFingerprint(fp.raw(), fp.hashed())).isTrue();
    }

    @Test
    void verifyFingerprint_failsWhenTampered() {
        var fp = service.generateFingerprint();
        assertThat(service.verifyFingerprint("tampered", fp.hashed())).isFalse();
    }

    @Test
    void verifyFingerprint_failsWhenNull() {
        assertThat(service.verifyFingerprint(null, "hash")).isFalse();
        assertThat(service.verifyFingerprint("raw", null)).isFalse();
    }

    @Test
    void createFingerprintCookie_hasCorrectAttributes() {
        var cookie = service.createFingerprintCookie("test", true);

        assertThat(cookie.getName()).isEqualTo("__Secure-Fgp");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
}
```

### 7.2 RefreshTokenService Tests

**File:** `src/test/java/contactapp/security/RefreshTokenServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository repository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RefreshTokenService service;

    @Test
    void createRefreshToken_revokesExistingTokens() {
        UUID userId = UUID.randomUUID();  // UUID per Phase 0.5 migration
        User user = new User();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.createRefreshToken(userId);

        verify(repository).revokeAllByUserId(userId);
    }

    @Test
    void createRefreshToken_setsCorrectExpiry() {
        UUID userId = UUID.randomUUID();  // UUID per Phase 0.5 migration
        User user = new User();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefreshToken token = service.createRefreshToken(userId);

        assertThat(token.getExpiryDate()).isAfter(Instant.now());
        assertThat(token.getExpiryDate()).isBefore(Instant.now().plus(Duration.ofDays(8)));
    }

    @Test
    void findValidToken_returnsEmptyForExpired() {
        // Use constructor with test data - no Lombok builders
        User testUser = new User();
        RefreshToken expired = new RefreshToken(
            testUser,
            "expired-token",
            Instant.now().minus(Duration.ofHours(1))
        );
        when(repository.findByTokenAndRevokedFalse("token")).thenReturn(Optional.of(expired));

        assertThat(service.findValidToken("token")).isEmpty();
    }

    @Test
    void revokeToken_setsRevokedFlag() {
        User testUser = new User();
        RefreshToken token = new RefreshToken(
            testUser,
            "test-token",
            Instant.now().plus(Duration.ofDays(7))
        );
        when(repository.findByToken("token")).thenReturn(Optional.of(token));

        service.revokeToken("token");

        assertThat(token.isRevoked()).isTrue();
        verify(repository).save(token);
    }
}
```

### 7.3 Integration Tests

**File:** `src/test/java/contactapp/api/AuthControllerRefreshIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class AuthControllerRefreshIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void login_returnsBothAccessAndRefreshCookies() {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/auth/login",
            new LoginRequest("testuser", "password"),
            AuthResponse.class
        );

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
            .anyMatch(c -> c.startsWith("auth_token="))
            .anyMatch(c -> c.startsWith("refresh_token="))
            .anyMatch(c -> c.startsWith("__Secure-Fgp=") || c.startsWith("Fgp="));
    }

    @Test
    void refresh_rotatesRefreshToken() {
        // Login to get initial tokens
        ResponseEntity<AuthResponse> loginResponse = login("testuser", "password");
        String refreshCookie = extractCookie(loginResponse, "refresh_token");

        // Call refresh
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
        ResponseEntity<AuthResponse> refreshResponse = restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST,
            new HttpEntity<>(headers), AuthResponse.class
        );

        // Old token should be revoked
        assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(refreshCookie)).isEmpty();

        // New token should be different
        String newRefreshCookie = extractCookie(refreshResponse, "refresh_token");
        assertThat(newRefreshCookie).isNotEqualTo(refreshCookie);
    }

    @Test
    void logout_revokesRefreshToken() {
        ResponseEntity<AuthResponse> loginResponse = login("testuser", "password");
        String refreshCookie = extractCookie(loginResponse, "refresh_token");

        // Logout
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
        restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
            new HttpEntity<>(headers), Void.class);

        // Token should be revoked
        assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(refreshCookie)).isEmpty();
    }

    @Test
    void expiredAccessToken_canRefreshWithValidRefreshToken() {
        // This test would require manipulating time or using short expiration
        // Implementation depends on test infrastructure
    }
}
```

### 7.4 CLI Tests

**File:** `scripts/tests/test_cs_cli_ssl.py`

> **Note:** Tests run from project root with `tmp_path` used only for output files. This matches the "Chosen strategy" in Section 16.

```python
import subprocess
from pathlib import Path
import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent  # Adjust to actual project root

@pytest.mark.integration
def test_setup_ssl_creates_keystore(tmp_path, monkeypatch):
    """Test that setup-ssl creates keystore and certificate files."""
    # Run from project root, redirect output to tmp_path via env var
    monkeypatch.setenv("CS_SSL_OUTPUT_ROOT", str(tmp_path))

    # Create expected directory structure in tmp output
    (tmp_path / "src/main/resources").mkdir(parents=True)

    result = subprocess.run(
        ["python", "-m", "scripts.cs_cli", "setup-ssl"],
        cwd=PROJECT_ROOT,
        capture_output=True, text=True
    )

    assert result.returncode == 0
    assert (tmp_path / "src/main/resources/local-ssl.p12").exists()
    assert (tmp_path / "certs/local-cert.crt").exists()

@pytest.mark.integration
def test_setup_ssl_shows_trust_instructions(tmp_path, monkeypatch):
    """Test that setup-ssl prints browser trust instructions."""
    monkeypatch.setenv("CS_SSL_OUTPUT_ROOT", str(tmp_path))
    (tmp_path / "src/main/resources").mkdir(parents=True)

    result = subprocess.run(
        ["python", "-m", "scripts.cs_cli", "setup-ssl"],
        cwd=PROJECT_ROOT,
        capture_output=True, text=True
    )

    assert "macOS" in result.stdout or "Windows" in result.stdout
```

---

## Updated Files Summary

### Phase 0 (Critical Pre-Implementation Fixes)

| Category          | File                                                                     | Action                                         |
|-------------------|--------------------------------------------------------------------------|------------------------------------------------|
| **Code**          | `src/main/java/contactapp/security/User.java`                            | MODIFY (@JsonIgnore, id Long→UUID)             |
|                   | `src/main/java/contactapp/security/SecurityConfig.java`                  | MODIFY (AccessDeniedHandler for 403)           |
|                   | `src/main/java/contactapp/security/JwtService.java`                      | MODIFY (clock skew, issuer/audience)           |
|                   | `ui/contact-app/src/lib/api.ts`                                          | MODIFY (fix 403 - don't auto-logout)           |
| **Migration**     | `src/main/resources/db/migration/common/V15__users_uuid_id.sql`          | CREATE (users.id BIGINT→UUID)                  |

### Main Implementation (Phases A-C)

| Category          | File                                                                     | Action                                     |
|-------------------|--------------------------------------------------------------------------|--------------------------------------------|
| **Code**          | `scripts/cs_cli.py`                                                      | ADD setup-ssl command                      |
|                   | `scripts/runtime_env.py`                                                 | MODIFY (use secrets.token_hex(32) for dev JWT) |
|                   | `src/main/java/contactapp/config/SchedulingConfig.java`                  | CREATE (@EnableScheduling for cleanup job) |
|                   | `src/main/java/contactapp/security/TokenFingerprintService.java`         | CREATE                                     |
|                   | `src/main/java/contactapp/security/RefreshToken.java`                    | CREATE                                     |
|                   | `src/main/java/contactapp/security/RefreshTokenRepository.java`          | CREATE                                     |
|                   | `src/main/java/contactapp/security/RefreshTokenService.java`             | CREATE                                     |
|                   | `src/main/resources/db/migration/common/V16__create_refresh_tokens.sql`  | CREATE (after V15 UUID migration)          |
|                   | `src/main/java/contactapp/security/JwtService.java`                      | MODIFY (add fingerprint, 20 call sites)    |
|                   | `src/main/java/contactapp/security/JwtAuthenticationFilter.java`         | MODIFY                                     |
|                   | `src/main/java/contactapp/api/AuthController.java`                       | MODIFY (3 generateToken calls)             |
|                   | `src/main/java/contactapp/security/SecurityConfig.java`                  | MODIFY (401 entry point)                   |
|                   | `src/main/resources/application.yml`                                     | MODIFY                                     |
|                   | `ui/contact-app/src/lib/api.ts`                                          | MODIFY (retry after refresh)               |
| **Documentation** | `docs/adrs/ADR-0052-production-auth-system.md`                           | CREATE                                     |
|                   | `docs/adrs/README.md`                                                    | MODIFY (add ADR-0052)                      |
|                   | `README.md`                                                              | MODIFY (add Security Architecture section) |
| **Tests**         | `src/test/java/contactapp/security/TokenFingerprintServiceTest.java`     | CREATE                                     |
|                   | `src/test/java/contactapp/security/RefreshTokenServiceTest.java`         | CREATE                                     |
|                   | `src/test/java/contactapp/security/JwtServiceTest.java`                  | MODIFY (17 generateToken calls)            |
|                   | `src/test/java/contactapp/api/AuthControllerRefreshIntegrationTest.java` | CREATE                                     |
|                   | `scripts/tests/test_cs_cli_ssl.py`                                       | CREATE                                     |

---

## Plan Review & Validation

**Short answer:** Yes, this is a solid, production-grade plan that lines up with OWASP guidance and common industry practice. There are a couple of small correctness and ergonomics details to fix before implementation, but the architecture itself is sound.

### 1. HTTPS + Self-Signed Cert ✅

**What we're doing:**
- `./cs setup-ssl` generates a PKCS12 keystore with a self-signed cert for localhost
- Spring Boot is wired to use `server.ssl.*` with `SSL_ENABLED` and `local-ssl.p12`
- Dev profile can turn SSL on by default, prod can terminate TLS at the proxy

This is exactly how typical Spring apps handle local HTTPS with Java keytool, and it is consistent with references on configuring HTTPS with self-signed certs in Spring Boot.

**Security requirements:**
- Treat `local-ssl.p12` as dev-only and either ignore it in git or clearly mark it as non-prod
- In CI, run with `SSL_ENABLED=false` so tests do not depend on the keystore being present

### 2. Dual Token, DB-Backed Refresh, Short-Lived Access ✅

OWASP's JWT cheat sheet explicitly recommends:
- Short-lived access tokens
- Refresh tokens stored server-side (often in a database) so they can be revoked and rotated

**Our plan matches that:**
- Access token: 15 minutes, in an HttpOnly cookie
- Refresh token: 7 days, random UUID, stored in `refresh_tokens` table
- Rotation on each refresh, with revocation of the old token
- Scheduled cleanup of expired tokens

**Architecturally this provides:**
- Bounded exposure window for a stolen access token
- Server-side control to kill sessions via refresh token revocation
- Better UX via 7-day "stay logged in"

### 3. Token Fingerprinting / Binding ✅

We're implementing token binding:
- Random fingerprint stored in an HttpOnly cookie
- SHA-256 hash of that fingerprint stored as a claim in the JWT
- Every request, we recompute the hash from the cookie and compare to the claim

This is a legitimate hardening approach and aligns with OWASP's general recommendations for binding tokens to additional context to reduce replay risk.

The split between `__Secure-Fgp` (HTTPS) and `Fgp` (HTTP dev) is correct because browsers require that `__Secure-` cookies are set with `Secure` and over HTTPS or they will be rejected.

### 4. Cookie Hardening ✅

Using:
- `HttpOnly`
- `Secure` (in HTTPS)
- `SameSite=Lax` (with CSRF token protection as second layer)
- `__Secure-` prefix in prod

This is consistent with OWASP and MDN guidance. Lax is recommended over Strict when CSRF tokens are also in use, as Strict can break login redirects and deep links.

### 5. 401 vs 403 ✅

Adding an `AuthenticationEntryPoint` that returns 401 with a JSON body is correct and matches Spring Security's recommended pattern.

---

## Implementation Tweaks (Before Coding)

These are implementation details, not architecture changes. **Read these carefully before writing any code.**

### 1. HTTPS and Self-Signed Cert

The `./cs setup-ssl` + `server.ssl.*` configuration is exactly how Spring Boot is normally wired for HTTPS with a self-signed PKCS12 keystore created by keytool.

**Critical implementation details:**
- Add `local-ssl.p12` to `.gitignore` so the dev keystore never ends up in the repo
- In CI and most tests, run with `SSL_ENABLED=false` and `server.port` set to your normal HTTP port so tests do not depend on the keystore
- For prod, plan to terminate TLS at a reverse proxy or use a real CA cert; keep `SSL_ENABLED=false` when TLS is terminated upstream

### 2. Cookie Hardening and `__Secure-` Prefix

The `__Secure-` prefix has **strict browser requirements**:
- Must be set with `Secure` attribute
- Must be set from a secure (HTTPS) origin

Your `TokenFingerprintService` design with two names (`__Secure-Fgp` for HTTPS, `Fgp` for HTTP dev) is the right way to avoid browsers silently dropping the cookie in dev while still using the stronger prefix in real HTTPS.

**Critical:** Logout must clear **both** `__Secure-Fgp` and `Fgp` (already addressed in AuthController above).

### 3. Repository Methods - Concrete Implementation

The ADR assumes these methods exist on `RefreshTokenRepository`. Here's exactly how to implement them:

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Derived query - Spring Data generates this automatically
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    // Derived query - Spring Data generates this automatically
    Optional<RefreshToken> findByToken(String token);

    // Custom query - must be explicit JPQL
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
    void revokeAllByUserId(@Param("userId") UUID userId);

    // Custom query - cleanup ALL expired tokens (keeps table bounded)
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiryDate < :now")
    void deleteAllExpired(@Param("now") Instant now);
}
```

### 4. Enable Scheduling - Required Configuration

You have `@Scheduled(cron = "0 0 * * * *")` on `cleanupExpiredTokens`. **This will silently do nothing** unless scheduling is enabled globally:

```java
@Configuration
@EnableScheduling
public class SchedulingConfig { }
```

Add this config class or the cleanup will never run.

### 5. Flyway Migration Version

You call it `V16__create_refresh_tokens.sql` (after V15 UUID migration). That must be **strictly greater** than all existing migrations or Flyway will skip it. Confirm your current top migration number and keep this one as the next.

### 6. Token Fingerprinting - API Migration Warning

⚠️ **Breaking Change Risk**

Your ADR uses:
```java
public String generateToken(UserDetails userDetails, String fingerprintHash)
```

You almost certainly already have a `generateToken(UserDetails)` in `JwtService`. Before you implement, decide one of:

**Option A (Recommended):** Update all call sites to pass a fingerprint hash and delete the old method
```java
// Delete the old method entirely
// public String generateToken(UserDetails userDetails) { ... }

// Only keep the new one
public String generateToken(UserDetails userDetails, String fingerprintHash) { ... }
```

**Option B (Transition):** Keep the old method and have it delegate with `fingerprintHash = null`, then treat null as "no binding used yet" in the filter
```java
public String generateToken(UserDetails userDetails) {
    return generateToken(userDetails, null);  // Legacy - no fingerprint
}
```

Leaving two slightly different behaviors in production for different call sites is easy to get wrong. **In a small app, strongly prefer Option A** - migrate all callers and remove the old overload.

### 7. Filter Behavior and Status Codes

In the filter you do:
- Extract JWT
- Extract fingerprint claim and cookie
- If mismatch, log and `filterChain.doFilter` without setting auth

That is fine, but make sure the rest of your stack handles missing authentication as "not logged in" and returns a 401 via the `AuthenticationEntryPoint` you defined.

**Keep strictly to these semantics:**
- **401** for unauthenticated (no or invalid token after checks)
- **403** for authenticated but unauthorized (user lacks required role or permission)

Your filters should **not** convert auth failures into 403. Let the entry point handle them as 401.

### 8. Integration Tests and Cookie Names

Tests assert `startsWith("__Secure-Fgp=")` but under HTTP/SSL_DISABLED this will be `Fgp=`. Make tests tolerant of both:
```java
.anyMatch(c -> c.startsWith("__Secure-Fgp=") || c.startsWith("Fgp="))
```

### 9. CLI Tests - Module Path Warning

The CLI tests that spawn `python -m scripts.cs_cli` inside `tmp_path` will fail unless the module path is available from that working directory.

**Options:**
- Run those tests from the project root and use a temporary directory only for the keystore and cert outputs
- Mark them as slower integration tests instead of unit tests
- Use absolute imports and ensure `PYTHONPATH` is set correctly

### 10. Keystore and Secrets Hygiene

- Dev keystore password (`changeit`) is only for the local dev keystore
- Never use `changeit` in any environment that faces the network

**Decision:** Add to `.gitignore`:
```
# Dev SSL keystore - regenerated per machine
src/main/resources/local-ssl.p12
certs/
```

### 11. Front-End Refresh Flow

Ensure:
- Never expose tokens to JS (already using HttpOnly cookies ✓)
- Handle case where `/refresh` itself returns 401 by redirecting to `/login?reason=sessionExpired`
- The frontend must not retry `/refresh` infinitely if it keeps failing

**Detailed Frontend Behavior (solves AUTH-R01 and SECURITY-002):**
- On 401 from any API call:
  1. If no refresh attempt has been made yet, call `/api/auth/refresh`
  2. If refresh succeeds, replay the original request once
  3. If refresh fails with 401, clear auth state and redirect to `/login?reason=sessionExpired`
- On 403 (Forbidden): Show authorization error but **do not** clear the session

### 12. SameSite Decision: Keep Lax

**AUDIT FINDING:** The current codebase uses `SameSite=Lax` everywhere:
- `AuthController.java:349` - `sameSite("Lax")`
- `application.yml:102` - `same-site: lax`
- `SecurityConfig.java:137` - `sameSite("Lax")`

**Decision: Keep `SameSite=Lax`** (align ADR with codebase)

**Rationale:**
- OWASP and MDN recommend Lax as a practical default when combined with CSRF token protection
- Strict can break login redirects, deep links, and cross-site navigation UX
- We already have CSRF token protection as a second defense layer
- Lax provides sufficient CSRF protection for most attack vectors

**Current posture:**
- `SameSite=Lax` + CSRF token = defense in depth
- All auth cookies, session cookies, and fingerprint cookies use Lax

**Future hardening (optional):**
- After verifying UX with login redirects and deep links, consider upgrading to Strict
- Document any flows that break with Strict before making the change

### 13. Cleanup Strategy Clarification

The cleanup query currently only deletes tokens that are **both expired AND revoked**:
```java
@Query("DELETE FROM RefreshToken t WHERE t.expiryDate < :now AND t.revoked = true")
void deleteAllExpiredAndRevoked(@Param("now") Instant now);
```

This means:
- ✅ Expired + revoked → cleaned up
- ❌ Expired but never revoked → **never cleaned** (table grows unbounded)

**Decision:** For bounded table size, change to delete **all expired tokens**:
```java
@Query("DELETE FROM RefreshToken t WHERE t.expiryDate < :now")
void deleteAllExpired(@Param("now") Instant now);
```

This is simpler and keeps the table bounded. Rename method to `deleteAllExpired`.

### 14. JWT Secret Configuration

The snippet `${JWT_SECRET:...}` is ambiguous. Make explicit:

```yaml
jwt:
  secret: ${JWT_SECRET:}  # No default - fails fast if missing in prod
```

**Dev mode:** `runtime_env.py` will generate a random secret using `secrets.token_hex(32)` at startup.

**Prod mode:** Requires `JWT_SECRET` environment variable. Application fails to start if missing.

This aligns with SECURITY-001 and SECURITY-003 findings.

### 15. generateToken Call Sites (Audit Result)

**Total: 20 call sites require updating:**

| Location              | Count | Notes                                  |
|-----------------------|-------|----------------------------------------|
| `AuthController.java` | 3     | Lines 160, 219, 284 - production code  |
| `JwtServiceTest.java` | 17    | Test methods - will fail until updated |

**User entity note:** `User` directly implements `UserDetails`, so `user` can be passed directly to `generateToken(UserDetails, String)` without casting.

### 16. CLI Tests Execution Strategy

**Chosen strategy:** Run CLI tests as **integration tests from project root**.

- Tests use `tmp_path` only for output files (keystore, cert), not as working directory
- CI executes from repository root with `PYTHONPATH` set correctly
- Mark as integration tests in pytest markers: `@pytest.mark.integration`

---

## Conclusion

From a correctness and security perspective:
- ✅ HTTPS + self-signed for dev: correct
- ✅ Short-lived access + DB-backed refresh token: exactly what OWASP suggests
- ✅ Token fingerprinting: a reasonable, well-motivated hardening step
- ✅ Cookie flags and prefixes: correct and standards compliant
- ✅ Documentation and test plan: thorough

**This is a good plan.**

The phased rollout keeps each step debuggable without compromising the architecture:
1. **Phase A:** HTTPS + 401 entry point, keep single JWT cookie
2. **Phase B:** Add refresh tokens
3. **Phase C:** Add fingerprinting
4. **Phase D:** Docs and tests

---

## External Security Review (2025-12-03)

> **Reviewer Assessment:** This ADR is already very close to a production-grade design for a Spring Boot JWT stack. The remaining gaps are mainly around crypto detail, operational hardening, and a few clarifications—not fundamental architecture.

### Review Summary by Section

| Section | Verdict | Notes |
|---------|---------|-------|
| HTTPS & Certificates | ✅ Production-grade | RSA 2048 for dev TLS is acceptable per NIST/OWASP guidance |
| JWT Model & Secrets | ✅ Production-grade | Matches OWASP JWT cheat sheet: short-lived access + server-side refresh |
| Refresh Tokens & DB | ✅ Production-grade | Normal production pattern: opaque tokens, stored in DB, revocable, rotated |
| Token Fingerprinting | ✅ Better than typical | Real mitigation for token replay and sidejacking |
| Cookies & SameSite | ✅ Production-grade | SameSite=Lax + CSRF tokens is sound defense-in-depth |
| 401 vs 403 Semantics | ✅ Correct | Aligns with REST/Spring Security semantics |
| CLI & Tests | ✅ Production mindset | Explicit coverage for auth paths supports regression safety |

### Hardening Recommendations (Incremental, Not Blocking)

1. **HTTPS & Dev Keystore**
   - ✅ Already planned: Add `local-ssl.p12` and `certs/` to `.gitignore`
   - Consider parameterizing keystore password via `CS_SSL_KEYSTORE_PASSWORD` env var

2. **JWT Secret Validation**
   - ✅ **Already implemented** in `JwtService.java:64-88`:
     - Fails fast if secret is null/blank
     - Rejects test secrets in production profile
     - Enforces minimum 32-byte (256-bit) key length for HMAC-SHA256
   - Recommendation: Document algorithm choice (HS256) explicitly in ADR

3. **Future Key Rotation**
   - Add `kid` (Key ID) claim for future multi-key support
   - Support multiple active secrets during rotation window
   - Consider KMS integration for production secrets management

4. **Refresh Token Reuse Detection (Optional)**
   - Current flow is safe: reused revoked token returns 401
   - Advanced option: Log high-severity event and force password reset on reuse

5. **Operational Concerns (Adjacent ADRs)**
   - Rate limiting on login (✅ already exists in codebase)
   - Audit logging for security events
   - Token revocation on password change

### Codebase Audit Findings

| Component | Current State | ADR Proposes |
|-----------|--------------|--------------|
| `JwtService.java` | ✅ Has 32-byte minimum validation | No change needed |
| `SecurityConfig.java` | Uses SameSite=Lax | ✅ ADR aligns |
| `AuthController.java` | Sliding-window refresh | DB-backed refresh tokens |
| `JwtAuthenticationFilter.java` | No fingerprint check | Add fingerprint verification |
| `runtime_env.py` | Hardcoded dev secret | Use `secrets.token_hex(32)` |
| `cs_cli.py` | No SSL command | Add `./cs setup-ssl` |
| `RefreshToken.java` | Does not exist | Create entity + migration |
| `TokenFingerprintService.java` | Does not exist | Create service |

### Final Verdict

**This is a good plan.** The architecture is sound and follows industry best practices:

- ✅ HTTPS + self-signed for dev: correct approach
- ✅ Short-lived access + DB-backed refresh: exactly what OWASP recommends
- ✅ Token fingerprinting: real security value, not theater
- ✅ Cookie flags and prefixes: standards-compliant
- ✅ Phased rollout: reduces implementation risk

The suggested hardening items are **incremental improvements on an already production-grade design**, not fixes to a broken architecture.

---

## Future Enhancements (Separate ADRs)

The following advanced hardening topics are **not blockers** to this ADR but represent natural follow-on work for a mature production system. Each should be tracked as a separate ADR.

### 1. Key Rotation and `kid` Support

**Priority:** Medium | **Effort:** Medium

- Add `kid` (Key ID) header claim to JWTs
- Support multiple active signing keys during rotation window
- Define rotation schedule (e.g., quarterly) and key retirement process
- Consider AWS KMS, HashiCorp Vault, or similar for key storage
- Implement graceful fallback: try current key first, then previous key

```java
// Example: JWT with kid header
return Jwts.builder()
    .header().keyId(currentKeyId).and()
    .subject(userDetails.getUsername())
    // ... other claims
    .signWith(getSigningKey(currentKeyId))
    .compact();
```

### 2. Refresh Token Reuse Detection

**Priority:** Medium | **Effort:** Low

Current behavior: Reusing a revoked refresh token returns 401 (safe default).

Advanced enhancement:
- Detect when a **revoked** refresh token is presented (potential theft indicator)
- Log high-severity security event with user ID, IP, timestamp
- Optionally: Revoke **all** refresh tokens for that user (force re-login on all devices)
- Optionally: Trigger account security review or require password reset

```java
public Optional<RefreshToken> findValidToken(String token) {
    Optional<RefreshToken> found = repository.findByToken(token);
    if (found.isPresent() && found.get().isRevoked()) {
        // SECURITY: Revoked token reuse detected - potential theft
        securityEventLogger.logTokenReuseAttempt(found.get().getUser().getId());
        repository.revokeAllByUserId(found.get().getUser().getId()); // Nuclear option
    }
    return found.filter(t -> !t.isRevoked() && t.getExpiryDate().isAfter(Instant.now()));
}
```

### 3. Global Session Invalidation Hooks

**Priority:** High | **Effort:** Low

Revoke all user sessions on security-sensitive events:

| Event | Action |
|-------|--------|
| Password change | Revoke all refresh tokens for user |
| Account disable/lock | Revoke all refresh tokens for user |
| Email change | Optionally revoke (configurable) |
| Role/permission change | Optionally revoke (configurable) |
| User-initiated "log out everywhere" | Revoke all refresh tokens for user |

```java
// In UserService or PasswordService
public void changePassword(UUID userId, String newPassword) {
    // ... update password
    refreshTokenService.revokeAllByUserId(userId); // Invalidate all sessions
    securityEventLogger.logPasswordChange(userId);
}
```

### 4. Rate Limiting and Lockout Policy

**Priority:** High | **Effort:** Medium

Auth endpoints need stricter limits than general API:

| Endpoint | Suggested Limit | Lockout |
|----------|-----------------|---------|
| `POST /api/auth/login` | 5 requests/minute/IP | After 10 failures: 15-min lockout |
| `POST /api/auth/register` | 3 requests/minute/IP | CAPTCHA after 2 attempts |
| `POST /api/auth/refresh` | 10 requests/minute/user | None (rotation is normal) |
| `POST /api/auth/logout` | 10 requests/minute/user | None |

Implementation options:
- **Application-level:** Bucket4j or Resilience4j (already have RateLimitingFilter)
- **Infrastructure-level:** API gateway, WAF, or reverse proxy (nginx, Cloudflare)

Consider progressive lockout: 1 min → 5 min → 15 min → 1 hour.

### 5. Security Logging and Monitoring

**Priority:** High | **Effort:** Medium

Structured logging for security events enables SIEM integration and incident response:

| Event | Log Level | Fields |
|-------|-----------|--------|
| Login success | INFO | userId, IP, userAgent, timestamp |
| Login failure | WARN | username (not password!), IP, reason |
| Token refresh | DEBUG | userId, oldTokenId, newTokenId |
| Fingerprint mismatch | WARN | userId, IP, expected hash prefix |
| Refresh token reuse | ERROR | userId, IP, tokenId, revokedAt |
| Logout | INFO | userId, IP |
| Password change | INFO | userId, IP |

```java
@Slf4j
public class SecurityEventLogger {
    public void logLoginSuccess(User user, HttpServletRequest request) {
        log.info("security.login.success userId={} ip={} userAgent={}",
            user.getId(), getClientIp(request), request.getHeader("User-Agent"));
    }

    public void logFingerprintMismatch(String userId, String expectedHashPrefix) {
        log.warn("security.fingerprint.mismatch userId={} expectedHashPrefix={}",
            userId, expectedHashPrefix);
    }
}
```

### 6. Operational TLS Posture

**Priority:** Medium | **Effort:** Low (infrastructure)

This ADR covers application-level TLS for local dev. Production TLS is typically handled at the infrastructure layer:

| Layer | Responsibility |
|-------|---------------|
| **Reverse proxy / Ingress** | Terminate TLS, enforce TLS 1.2+ minimum |
| **Load balancer** | Certificate management (Let's Encrypt, ACM) |
| **Application** | Trust proxy headers (`X-Forwarded-Proto`), set `Secure` cookies |

Recommended cipher suites (TLS 1.2):
- `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`
- `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`

TLS 1.3 ciphers are secure by default.

**Spring Boot behind proxy:**
```yaml
server:
  forward-headers-strategy: framework  # Trust X-Forwarded-* headers
  tomcat:
    remote-ip-header: X-Forwarded-For
    protocol-header: X-Forwarded-Proto
```

---

## Implementation Checklist

> **Pre-coding verification items.** Check each before marking a phase complete.

### Phase 0 Prerequisites

- [x] **V15 migration handles existing data safely** - Uses add-column/backfill/swap-PK pattern, not destructive TRUNCATE *(2025-12-03)*
- [x] **pgcrypto extension enabled** - Migration includes `CREATE EXTENSION IF NOT EXISTS "pgcrypto"` *(2025-12-03)*
- [ ] **No legacy `Long` user ID references** - Search for `1L` sentinel values in tests, update to `UUID.randomUUID()` *(Batch 2)*

### Phase 0 Critical Fixes (Implemented 2025-12-03)

- [x] **`@JsonIgnore` on `User.password`** - Prevents password hash leaking in JSON serialization
- [x] **`AuthenticationEntryPoint` returns 401** - JSON response with UTF-8 encoding
- [x] **`AccessDeniedHandler` returns 403** - JSON response with UTF-8 encoding
- [x] **JWT clock skew tolerance** - 60 seconds via `.clockSkewSeconds(60)`
- [x] **JWT issuer/audience validation** - `contact-service` / `contact-service-api`
- [x] **Frontend 401 vs 403 handling** - 403 no longer clears session
- [x] **User.id UUID generation** - Portable `@PrePersist` with `UUID.randomUUID()`

### JwtService Changes

- [x] **All-or-nothing API change** - Delete old `generateToken(UserDetails)`, use only `generateToken(UserDetails, String fingerprintHash)` ✅ Batch 5
- [x] **Update all call sites** - 3 in `AuthController`, ~17 in test files ✅ Batch 5
- [x] **No mixed fingerprint/non-fingerprint code paths** in production ✅ Batch 5

### RefreshTokenRepository

- [ ] **Implement exactly as specified** - `deleteAllExpired(Instant now)` deletes ALL expired tokens, not just revoked
- [ ] **Verify query semantics** - `revokeAllByUserId(UUID)` uses `@Modifying` + `@Query` JPQL

### Scheduling

- [ ] **@EnableScheduling present** - Create `SchedulingConfig.java` with `@Configuration @EnableScheduling`
- [ ] **Package is component-scanned** - Class in `contactapp.config` or similar

### JJWT Version Compatibility

- [ ] **Verify API style matches dependency** - Modern builder style:
  ```java
  Jwts.parser()
      .verifyWith(getSignInKey())
      .clockSkewSeconds(60)  // Not setAllowedClockSkewSeconds
      .build()
      .parseSignedClaims(token)
  ```
- [ ] **Adjust method names if using older JJWT** (< 0.12.x uses different API)

### Test Assertions

- [ ] **Both fingerprint cookie names accepted**:
  ```java
  .anyMatch(c -> c.startsWith("__Secure-Fgp=") || c.startsWith("Fgp="));
  ```
- [ ] **Tests work in both HTTP and HTTPS modes**

### Git Hygiene

- [ ] **.gitignore updated**:
  ```
  src/main/resources/local-ssl.p12
  certs/
  ```
- [ ] **No secrets committed** - Dev keystore generated per-developer via `./cs setup-ssl`

### Frontend Behavior

- [ ] **401 handling** - One refresh attempt → logout with `reason=sessionExpired`
- [ ] **403 handling** - Show "Forbidden" error, do NOT clear session or redirect
- [ ] **No shortcuts** - Never treat 403 same as 401

### JWT Secret Validation

- [ ] **JwtService enforces**:
  - Secret not blank
  - Secret not known test value in prod (`devsecretkey...`)
  - Minimum 32 bytes for HS256
- [ ] **application.yml has no insecure default** - `${JWT_SECRET:}` (empty default, fail-fast)

### Flyway Migration Order

- [ ] **V15 = UUID migration** - `V15__users_uuid_id.sql`
- [ ] **V16 = Refresh tokens** - `V16__create_refresh_tokens.sql` (depends on V15)
- [ ] **Version numbers are next after existing migrations** - Check `flyway_schema_history` table

---

## ADR Status Tracking

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 0: Critical Fixes | ✅ Complete | @JsonIgnore, AccessDeniedHandler, clock skew, 403 fix (Batch 1 - 2025-12-03) |
| Phase 0.5: UUID + Claims | ✅ Complete | User.id → UUID, issuer/audience claims (Batch 1 - 2025-12-03) |
| Phase 0.6: UUID Cascade | ✅ Complete | Task.assigneeId → UUID, UserRepository → UUID, test fixes (Batch 2 - 2025-12-03) |
| Phase A: HTTPS Setup | ✅ Complete | ./cs setup-ssl, SSL config in application.yml (Batch 3 - 2025-12-03) |
| Phase B: Refresh Tokens | ✅ Complete | RefreshToken entity, RefreshTokenService, V16 migration, token rotation, scheduled cleanup (Batches 6-7 - 2025-12-03) |
| Phase C: Token Fingerprinting | ✅ Complete | TokenFingerprintService, JwtService fingerprint binding, JwtAuthenticationFilter verification (Batch 4 - 2025-12-03) |
| Phase C.5: Call Site Migration | ✅ Complete | Deleted old generateToken overloads, migrated AuthController (3 sites) and JwtServiceTest (18+ calls) to fingerprint-aware signature (Batch 5 - 2025-12-03) |
| Phase D: Docs + Tests | 🔲 Not Started | Test coverage, documentation updates |

---

*Review conducted against codebase commit 6ebabd6 and ADR version 2025-12-03.*
*Implementation checklist added 2025-12-03 based on final security review.*
