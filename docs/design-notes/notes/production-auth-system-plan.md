# Plan: Production-Grade Secure Authentication System

> **Status**: In Progress (Batches 1-6 Complete, Phases 0 + A + B + C Complete)
> **Last Updated**: 2025-12-06
> **Related ADRs**: ADR-0018, ADR-0038, ADR-0043, [ADR-0052](../../adrs/ADR-0052-production-auth-system.md)

---

## Phase/Batch Mapping

Phases represent logical security features; batches are implementation milestones.
Phase C was implemented before Phase B due to lower risk and simpler rollout.

| Phase | Description                | Batches   | Status       |
|-------|----------------------------|-----------|--------------|
| 0     | Critical fixes (UUID, 401) | Batch 1-2 | ✅ Complete  |
| A     | HTTPS setup                | Batch 3   | ✅ Complete  |
| C     | Token fingerprinting       | Batch 4-5 | ✅ Complete  |
| B     | Refresh tokens + TOCTOU    | Batch 6   | ✅ Complete  |
| D-G   | Docs, tests, frontend      | Batch 7-8 | 🔲 Remaining |

*Phase C preceded Phase B because fingerprinting is lower risk (additive feature)
while refresh tokens require more infrastructure (new DB table, cookie scope changes).*

---

## Implementation Progress

### ✅ Batch 1 Complete (2025-12-03) - Phase 0 Critical Fixes

| Item | Status | Details |
|------|--------|---------|
| `@JsonIgnore` on `User.password` | ✅ Done | Field and getter protected |
| `User.id` Long → UUID | ✅ Done | V16 migration (Postgres + H2) |
| `AuthenticationEntryPoint` (401) | ✅ Done | JSON response with UTF-8 |
| `AccessDeniedHandler` (403) | ✅ Done | JSON response with UTF-8 |
| JWT clock skew tolerance | ✅ Done | 60 seconds via `.clockSkewSeconds(60)` |
| JWT issuer/audience claims | ✅ Done | `contact-service` / `contact-service-api` |
| Frontend 401 vs 403 | ✅ Done | 403 no longer clears session |

### ✅ Batch 2 Complete (2025-12-03) - UUID Cascade Fixes

| Item | Status | Details |
|------|--------|---------|
| `UserRepository` methods | ✅ Done | Use `UUID` parameter types |
| `Task.assigneeId` | ✅ Done | Long → UUID (domain, entity, DTO, service, controller) |
| `@WithMockAppUser` | ✅ Done | `long id()` → `String id()` (UUID string) |
| H2 migration V16 fix | ✅ Done | Drop projects unique constraint before column drop |
| ActuatorEndpointsTest | ✅ Done | 403 → 401 for unauthenticated requests |
| JwtServiceTest | ✅ Done | Updated for 60-second clock skew tolerance |
| All tests pass | ✅ Done | 1109 tests |

### ✅ Batch 3 Complete (2025-12-03) - HTTPS Setup

| Item | Status | Details |
|------|--------|---------|
| SSL keystore generation | ✅ Done | `./cs setup-ssl` command implemented |
| Spring Boot SSL config | ✅ Done | Port 8443, PKCS12 keystore support |
| Dev profile HTTPS | ✅ Done | SSL enabled by default in dev mode |
| Certificate export | ✅ Done | Browser trust instructions provided |

### ✅ Batch 4 Complete (2025-12-03) - Token Fingerprinting (Phase C)

| Item | Status | Details |
|------|--------|---------|
| `TokenFingerprintService` | ✅ Done | SHA256 hashing, secure random generation |
| Fingerprint cookie support | ✅ Done | `__Secure-Fgp` (HTTPS) / `Fgp` (HTTP dev) |
| JWT fingerprint claim | ✅ Done | Hash stored in `fingerprint` claim |
| Filter verification | ✅ Done | `JwtAuthenticationFilter` validates match |
| `AuthController` integration | ✅ Done | Login sets fingerprint cookie + JWT claim |
| Cookie name logic | ✅ Done | Prefix based on secure flag |
| Unit tests | ✅ Done | `TokenFingerprintServiceTest` (1109 tests pass) |

### ✅ Batch 5 Complete (2025-12-03) - Call Site Migration (Phase C Complete)

| Item | Status | Details |
|------|--------|---------|
| Old adapter overloads deleted | ✅ Done | Removed deprecated `JwtService.generateToken(UserDetails)` |
| `AuthController` migrated | ✅ Done | 3 endpoints now use fingerprinting |
| `JwtServiceTest` migrated | ✅ Done | 18+ calls updated to new signature |
| Cookie max-age alignment | ✅ Done | Fingerprint cookie matches access token (30 min) |
| Phase C complete | ✅ Done | All fingerprinting call sites migrated |

**Phase C: Token Fingerprinting** is now fully complete. All production code and tests have been migrated to the new fingerprinting-enabled API.

### ✅ Batch 6 Complete (2025-12-06) - Refresh Tokens (Phase B)

| Item | Status | Details |
|------|--------|---------|
| `RefreshToken` entity | ✅ Done | UUID-based, with validation and revocation |
| `RefreshTokenRepository` | ✅ Done | CRUD + pessimistic locking query |
| `RefreshTokenService` | ✅ Done | Atomic validate-and-revoke for TOCTOU prevention |
| V17 migration | ✅ Done | refresh_tokens table (Postgres + H2) |
| `AuthController` refresh | ✅ Done | Uses atomic rotation to prevent race conditions |
| Scheduled cleanup | ✅ Done | Hourly job deletes expired tokens |

### 🔲 Remaining Batches

- **Batch 7**: Frontend refresh flow
- **Batch 8**: Tests + documentation

---

## Problem

- No HTTPS - cookies transmitted insecurely
- No token fingerprinting - stolen tokens can be reused
- No refresh token mechanism - users must re-login constantly
- Backend returns 403 instead of 401 for expired tokens
- No easy way to set up SSL for development

## The Complete Secure Solution

Based on OWASP best practices and industry standards:

1. **HTTPS everywhere** - self-signed cert for dev, real cert for prod
2. **Token fingerprinting** - SHA256 hash prevents stolen token reuse (OWASP sidejacking prevention)
3. **Database-backed refresh tokens** - with rotation on each use
4. **Proper cookie flags** - `HttpOnly; Secure; SameSite=Lax; __Secure-` prefix
5. **CLI setup command** - `./cs setup-ssl` generates certs automatically

**Sources:**
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [Spring Boot HTTPS Setup](https://www.codejava.net/frameworks/spring-boot/configure-https-with-self-signed-certificate)
- [BezKoder Refresh Tokens](https://www.bezkoder.com/spring-boot-refresh-token-jwt/)

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
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofMinutes(30)) // Match access token expiry
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

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Getters, setters, builder pattern
}
```

### 3.2 Flyway Migration

**File:** `src/main/resources/db/migration/common/V12__create_refresh_tokens.sql`

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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

    public RefreshToken createRefreshToken(UUID userId) {
        // Revoke existing tokens (single active session)
        repository.revokeAllByUserId(userId);

        RefreshToken token = RefreshToken.builder()
            .user(userRepository.getReferenceById(userId))
            .token(UUID.randomUUID().toString())
            .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
            .build();

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
        repository.deleteAllExpiredAndRevoked(Instant.now());
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
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofMillis(jwtService.getExpiration()))
        .build();

    ResponseCookie fingerprintCookie = fingerprintService.createFingerprintCookie(
        fingerprint.raw(), cookieSecure);

    ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken.getToken())
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
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
  secret: ${JWT_SECRET:...}
  expiration: 900000           # 15 minutes access token
  refresh-expiration: 604800000  # 7 days refresh token

server:
  port: ${SERVER_PORT:8443}
  ssl:
    enabled: ${SSL_ENABLED:true}
    key-store: classpath:local-ssl.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: local_ssl
```

---

## Files Summary

| File | Action |
|------|--------|
| `scripts/cs_cli.py` | ADD `setup-ssl` command |
| `src/main/java/contactapp/security/TokenFingerprintService.java` | CREATE |
| `src/main/java/contactapp/security/RefreshToken.java` | CREATE |
| `src/main/java/contactapp/security/RefreshTokenRepository.java` | CREATE |
| `src/main/java/contactapp/security/RefreshTokenService.java` | CREATE |
| `src/main/resources/db/migration/common/V12__create_refresh_tokens.sql` | CREATE |
| `src/main/java/contactapp/security/JwtService.java` | MODIFY (add fingerprint) |
| `src/main/java/contactapp/security/JwtAuthenticationFilter.java` | MODIFY (verify fingerprint) |
| `src/main/java/contactapp/api/AuthController.java` | MODIFY (refresh endpoint, cookies) |
| `src/main/java/contactapp/security/SecurityConfig.java` | MODIFY (401 entry point) |
| `src/main/resources/application.yml` | MODIFY (SSL, refresh config) |
| `ui/contact-app/src/lib/api.ts` | MODIFY (retry after refresh) |

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

### Phase A: HTTPS + 401 Entry Point ✅ COMPLETE (Batch 1-3, 2025-12-03)
- ✅ Add `./cs setup-ssl` command
- ✅ Configure Spring Boot SSL
- ✅ Add `AuthenticationEntryPoint` for proper 401 responses
- ✅ Keep current single JWT cookie (no refresh yet)
- ✅ **Testable checkpoint**: HTTPS works, 401 returned for expired tokens

### Phase B: Refresh Token System
- Create `RefreshToken` entity and migration
- Create `RefreshTokenService` with rotation
- Update `AuthController` with `/refresh` endpoint
- Update frontend `api.ts` to call refresh
- **Testable checkpoint**: Users can stay logged in for 7 days

### Phase C: Token Fingerprinting ✅ COMPLETE (Batch 4-5, 2025-12-03)
- ✅ Create `TokenFingerprintService` (Batch 4)
- ✅ Add fingerprint claim to JWT (Batch 4)
- ✅ Add fingerprint verification to filter (Batch 4)
- ✅ Migrate all call sites to new API (Batch 5)
- ✅ Delete deprecated adapter overloads (Batch 5)
- ✅ **Testable checkpoint**: Stolen tokens rejected without matching cookie

**Implementation Notes:**
- Fingerprint generation uses SecureRandom with 50-byte random values
- SHA256 hashing for JWT claim storage
- Cookie naming supports both `__Secure-Fgp` (HTTPS) and `Fgp` (HTTP dev)
- Constant-time comparison using `MessageDigest.isEqual()`
- Filter validates fingerprint before processing JWT claims
- All production code and tests migrated (AuthController, JwtServiceTest)
- Cookie max-age aligned: 30 minutes for both access token and fingerprint
- All 1109 tests passing including `TokenFingerprintServiceTest`

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
- **Do NOT ship** `local-ssl.p12` to production
- Either:
  - Terminate TLS at reverse proxy/ingress (common)
  - Use real cert from Let's Encrypt/CA
- Set `SSL_ENABLED=false` if TLS terminated at proxy

---

## Security Checklist

- [x] HTTPS with TLS 1.2+ (self-signed for dev, real cert for prod) - **Batch 3 ✅**
- [x] Token fingerprinting (OWASP sidejacking prevention) - **Batch 4 ✅**
- [x] Short-lived access tokens (15 min) - **Batch 1 ✅**
- [ ] Long-lived refresh tokens (7 days, database-backed) - *Batch 5-6*
- [ ] Token rotation on refresh - *Batch 5-6*
- [x] Hardened cookies: `HttpOnly; Secure; SameSite=Lax` - **Batch 1 ✅**
- [x] `__Secure-Fgp` for HTTPS, `Fgp` for HTTP dev (cookie prefix fix) - **Batch 4 ✅**
- [x] 401 for unauthenticated, 403 for unauthorized - **Batch 1 ✅**
- [ ] Revoke refresh token on logout - *Batch 5-6*
- [ ] Scheduled cleanup of expired tokens - *Batch 5-6*
- [x] Clear dev vs prod separation (changeit password is dev-only) - **Batch 3 ✅**

---

## Phase 7: Documentation

### 7.1 ADR-0052: Production-Grade Authentication System

**File:** `docs/adrs/ADR-0052-production-auth-system.md`

```markdown
# ADR-0052: Production-Grade Authentication System

**Status:** Accepted | **Date:** 2025-12-03 | **Owners:** [Author]

**Related:** ADR-0018 (Auth Model), ADR-0038 (Auth Implementation), ADR-0043 (HttpOnly Cookies)

## Context

The existing JWT authentication had several security gaps:
- No HTTPS in development (cookies transmitted insecurely)
- No token fingerprinting (stolen tokens could be reused)
- Single token with no refresh mechanism (frequent re-logins)
- Backend returned 403 instead of 401 for expired tokens

## Decision

Implement a production-grade authentication system following OWASP JWT best practices:

1. **HTTPS Everywhere**: Self-signed certificates for dev via `./cs setup-ssl`
2. **Token Fingerprinting**: SHA256 hash of random value stored in JWT, raw value in `__Secure-Fgp` HttpOnly cookie
3. **Dual Token Architecture**:
   - Short-lived access token (15 min) in HttpOnly cookie
   - Long-lived refresh token (7 days) in database + HttpOnly cookie
4. **Token Rotation**: New refresh token issued on each refresh
5. **Proper HTTP Status Codes**: 401 for unauthenticated, 403 for unauthorized

## Consequences

### Positive
- Tokens cannot be reused if stolen (fingerprint binding)
- Users stay logged in for 7 days without re-entering password
- Compromised refresh tokens can be revoked server-side
- HTTPS prevents MITM attacks on cookies

### Negative
- More complex authentication flow
- Database required for refresh tokens
- Users must trust self-signed cert for local dev

## Alternatives Considered

1. **Session-based auth**: Rejected - doesn't scale horizontally
2. **Longer JWT expiration**: Rejected - increases exposure window
3. **localStorage tokens**: Rejected - vulnerable to XSS
```

### 7.2 Update README.md

**File:** `README.md` - Add new section

```markdown
## Security Architecture

### Authentication Flow

This application uses a production-grade JWT authentication system following [OWASP best practices](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html):

1. **HTTPS Required**: All traffic encrypted with TLS
2. **Dual Token System**:
   - **Access Token** (15 min): Short-lived JWT in HttpOnly cookie
   - **Refresh Token** (7 days): Database-backed, rotated on each use
3. **Token Fingerprinting**: Prevents stolen token reuse via SHA256 binding
4. **Hardened Cookies**: `HttpOnly; Secure; SameSite=Lax`

### Local Development Setup

```bash
# Generate SSL certificate (one-time)
./cs setup-ssl

# Trust the certificate:
# macOS: open certs/local-cert.crt → Keychain → Always Trust
# Windows: certutil -addstore root certs\local-cert.crt

# Start development server (HTTPS)
./cs dev
# Access: https://localhost:8443
```

### Token Lifecycle

| Token | Lifetime | Storage | Rotation |
|-------|----------|---------|----------|
| Access | 15 min | HttpOnly cookie | On refresh |
| Refresh | 7 days | Database + HttpOnly cookie | On each use |
| Fingerprint | 15 min | HttpOnly cookie (`__Secure-Fgp`) | On refresh |

### Security Features

- ✅ HTTPS with TLS 1.2+
- ✅ Token fingerprinting (OWASP sidejacking prevention)
- ✅ Database-backed refresh tokens with rotation
- ✅ Automatic token cleanup (hourly cron)
- ✅ Force logout capability (revoke all user tokens)
- ✅ 401/403 proper HTTP status codes
```

### 7.3 Update ADR Index

**File:** `docs/adrs/README.md` - Add to index

```markdown
### Security Enhancements (ADR-0052)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0052](ADR-0052-production-auth-system.md) | Production-Grade Authentication System | Accepted |
```

---

## Phase 8: Testing

### 8.1 TokenFingerprintService Tests

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

### 8.2 RefreshTokenService Tests

**File:** `src/test/java/contactapp/security/RefreshTokenServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository repository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RefreshTokenService service;

    @Test
    void createRefreshToken_revokesExistingTokens() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.createRefreshToken(userId);

        verify(repository).revokeAllByUserId(userId);
    }

    @Test
    void createRefreshToken_setsCorrectExpiry() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefreshToken token = service.createRefreshToken(userId);

        assertThat(token.getExpiryDate()).isAfter(Instant.now());
        assertThat(token.getExpiryDate()).isBefore(Instant.now().plus(Duration.ofDays(8)));
    }

    @Test
    void findValidToken_returnsEmptyForExpired() {
        RefreshToken expired = new RefreshToken();
        expired.setExpiryDate(Instant.now().minus(Duration.ofHours(1)));
        when(repository.findByTokenAndRevokedFalse("token")).thenReturn(Optional.of(expired));

        assertThat(service.findValidToken("token")).isEmpty();
    }

    @Test
    void revokeToken_setsRevokedFlag() {
        RefreshToken token = new RefreshToken();
        when(repository.findByToken("token")).thenReturn(Optional.of(token));

        service.revokeToken("token");

        assertThat(token.isRevoked()).isTrue();
        verify(repository).save(token);
    }
}
```

### 8.3 Integration Tests

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
            .anyMatch(c -> c.startsWith("__Secure-Fgp="));
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

### 8.4 CLI Tests

**File:** `scripts/tests/test_cs_cli_ssl.py`

```python
import subprocess
from pathlib import Path

def test_setup_ssl_creates_keystore(tmp_path, monkeypatch):
    """Test that setup-ssl creates keystore and certificate files."""
    monkeypatch.chdir(tmp_path)

    # Create expected directory structure
    (tmp_path / "src/main/resources").mkdir(parents=True)

    result = subprocess.run(
        ["python", "-m", "scripts.cs_cli", "setup-ssl"],
        capture_output=True, text=True
    )

    assert result.returncode == 0
    assert (tmp_path / "src/main/resources/local-ssl.p12").exists()
    assert (tmp_path / "certs/local-cert.crt").exists()

def test_setup_ssl_shows_trust_instructions(tmp_path, monkeypatch):
    """Test that setup-ssl prints browser trust instructions."""
    monkeypatch.chdir(tmp_path)
    (tmp_path / "src/main/resources").mkdir(parents=True)

    result = subprocess.run(
        ["python", "-m", "scripts.cs_cli", "setup-ssl"],
        capture_output=True, text=True
    )

    assert "macOS" in result.stdout or "Windows" in result.stdout
```

---

## Updated Files Summary

| File | Action |
|------|--------|
| **Code** | |
| `scripts/cs_cli.py` | ADD `setup-ssl` command |
| `src/main/java/contactapp/security/TokenFingerprintService.java` | CREATE |
| `src/main/java/contactapp/security/RefreshToken.java` | CREATE |
| `src/main/java/contactapp/security/RefreshTokenRepository.java` | CREATE |
| `src/main/java/contactapp/security/RefreshTokenService.java` | CREATE |
| `src/main/resources/db/migration/common/V12__create_refresh_tokens.sql` | CREATE |
| `src/main/java/contactapp/security/JwtService.java` | MODIFY |
| `src/main/java/contactapp/security/JwtAuthenticationFilter.java` | MODIFY |
| `src/main/java/contactapp/api/AuthController.java` | MODIFY |
| `src/main/java/contactapp/security/SecurityConfig.java` | MODIFY |
| `src/main/resources/application.yml` | MODIFY |
| `ui/contact-app/src/lib/api.ts` | MODIFY |
| **Documentation** | |
| `docs/adrs/ADR-0052-production-auth-system.md` | CREATE |
| `docs/adrs/README.md` | MODIFY (add ADR-0052) |
| `README.md` | MODIFY (add Security Architecture section) |
| **Tests** | |
| `src/test/java/contactapp/security/TokenFingerprintServiceTest.java` | CREATE |
| `src/test/java/contactapp/security/RefreshTokenServiceTest.java` | CREATE |
| `src/test/java/contactapp/api/AuthControllerRefreshIntegrationTest.java` | CREATE |
| `scripts/tests/test_cs_cli_ssl.py` | CREATE |
