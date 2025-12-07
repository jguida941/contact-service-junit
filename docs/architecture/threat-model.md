# Threat Model: Contact Suite Application

**Version**: 1.0
**Date**: 2025-12-01
**Owner**: Justin Guida

## 1. System Overview

The Contact Suite is a multi-tenant contact management application with:
- **Frontend**: React SPA served from same origin
- **Backend**: Spring Boot REST API with JWT authentication
- **Database**: PostgreSQL with per-user data isolation
- **Deployment**: Docker containers behind reverse proxy

### Architecture Diagram

```
┌─────────────────┐     HTTPS      ┌──────────────────┐
│   React SPA     │◄──────────────►│  Spring Boot API │
│  (Browser)      │                │  (Port 8080)     │
└─────────────────┘                └────────┬─────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │   PostgreSQL     │
                                   │   (Port 5432)    │
                                   └──────────────────┘
```

## 2. Trust Boundaries

| Boundary             | Description                               | Controls                                  |
|----------------------|-------------------------------------------|-------------------------------------------|
| **Browser ↔ API**    | Untrusted network; user-controlled client | TLS, CORS, CSRF tokens, JWT auth          |
| **API ↔ Database**   | Internal network; trusted connection      | Connection pooling, parameterized queries |
| **User ↔ User Data** | Multi-tenant isolation                    | Per-user FK, service-level filtering      |

## 3. Threat Actors

| Actor                        | Motivation                   | Capabilities                             |
|------------------------------|------------------------------|------------------------------------------|
| **Unauthenticated Attacker** | Account takeover, data theft | Network access, automated tools          |
| **Authenticated User**       | Access other users' data     | Valid JWT, API knowledge                 |
| **Malicious Script**         | XSS exploitation             | JavaScript execution in victim's browser |
| **Internal Attacker**        | Data exfiltration            | Database access, log access              |

## 4. Threats and Mitigations

### 4.1 Authentication Threats

| Threat                  | STRIDE   | Risk   | Mitigation                                            | Status      |
|-------------------------|----------|--------|-------------------------------------------------------|-------------|
| **Credential Stuffing** | Spoofing | High   | Rate limiting: 5 login attempts/min per IP            | Implemented |
| **Brute Force**         | Spoofing | High   | Rate limiting + password strength (upper/lower/digit) | Implemented |
| **Session Hijacking**   | Spoofing | High   | HttpOnly cookies, Secure flag, SameSite=Lax           | Implemented |
| **JWT Token Theft**     | Spoofing | Medium | HttpOnly cookie storage (not localStorage)            | Implemented |
| **Weak Passwords**      | Spoofing | Medium | Bean Validation regex requiring mixed characters      | Implemented |

### 4.2 Authorization Threats

| Threat                                      | STRIDE                 | Risk     | Mitigation                                       | Status      |
|---------------------------------------------|------------------------|----------|--------------------------------------------------|-------------|
| **Horizontal Privilege Escalation**         | Elevation              | Critical | Per-user FK on all data; service-level filtering | Implemented |
| **Vertical Privilege Escalation**           | Elevation              | High     | @PreAuthorize annotations; role checks           | Implemented |
| **IDOR (Insecure Direct Object Reference)** | Information Disclosure | High     | User ID extracted from JWT, not request          | Implemented |

### 4.3 Cross-Site Attacks

| Threat                                | STRIDE    | Risk   | Mitigation                                       | Status      |
|---------------------------------------|-----------|--------|--------------------------------------------------|-------------|
| **Cross-Site Scripting (XSS)**        | Tampering | High   | CSP: `script-src 'self'`; React auto-escapes     | Implemented |
| **Cross-Site Request Forgery (CSRF)** | Tampering | High   | CSRF tokens via CookieCsrfTokenRepository        | Implemented |
| **Clickjacking**                      | Tampering | Medium | X-Frame-Options: SAMEORIGIN; CSP frame-ancestors | Implemented |

### 4.4 Injection Threats

| Threat                | STRIDE    | Risk     | Mitigation                                  | Status      |
|-----------------------|-----------|----------|---------------------------------------------|-------------|
| **SQL Injection**     | Tampering | Critical | JPA parameterized queries; no raw SQL       | Implemented |
| **NoSQL Injection**   | Tampering | N/A      | Not applicable (PostgreSQL only)            | N/A         |
| **Command Injection** | Tampering | N/A      | No shell execution in application           | N/A         |
| **Log Injection**     | Tampering | Low      | User agent sanitization; structured logging | Implemented |

### 4.5 Information Disclosure

| Threat                    | STRIDE                 | Risk   | Mitigation                                      | Status      |
|---------------------------|------------------------|--------|-------------------------------------------------|-------------|
| **PII in Logs**           | Information Disclosure | Medium | PiiMaskingConverter masks phone/address         | Implemented |
| **Error Message Leakage** | Information Disclosure | Low    | Generic error messages; no stack traces in prod | Implemented |
| **User Enumeration**      | Information Disclosure | Low    | Generic "invalid credentials" message           | Implemented |

#### PII Retention and Masking Policy
- **Scope**: Application logs flow through `PiiMaskingConverter` which currently masks phone numbers
  and postal addresses. Email/username/IP masking is planned for a future release.
- **Strategy (Implemented)**:
  - Phone numbers: Shows only last 4 digits (`***-***-1234`)
  - Addresses: Preserves city and state, masks street and zip (`*** Cambridge, MA ***`)
- **Strategy (Planned)**:
  - Emails: First and last character of local-part preserved (`j***n@example.com` for `jenny.rosen@example.com`)
  - Usernames: First and last character preserved (`j***n` for `jrosen`)
  - IPs: Hashed with per-day salt (`hash:d7042d2f`)
- **Exceptions**: Security/audit logs that must capture exact values are written to an isolated
  index behind RBAC; retention is 30 days and requires SOC approval to access.
- **Retention**: Application logs retain 14 days online, 30 days in cold storage. Users can request
  erasure via support; delete jobs scrub entries from cold storage as well.

Example log redaction (current implementation):

```
Before: phone=555-867-5309, address=123 Main St, Portland, OR 97214
After:  phone=***-***-5309, address=*** Portland, OR ***
```

### 4.6 Availability Threats

| Threat                       | STRIDE            | Risk   | Mitigation                                 | Status      |
|------------------------------|-------------------|--------|--------------------------------------------|-------------|
| **Denial of Service (API)**  | Denial of Service | High   | Rate limiting: 100 req/min per user        | Implemented |
| **Denial of Service (Auth)** | Denial of Service | High   | Rate limiting: 5 login, 3 register per min | Implemented |
| **Resource Exhaustion**      | Denial of Service | Medium | Connection pooling; request size limits    | Implemented |

## 5. CORS Security Model

### Allowed Origins
- Development: `http://localhost:5173`, `http://localhost:8080`
- Production: Configured via `cors.allowed-origins` environment variable

### CORS Policy
```java
allowedOrigins: [configured origins only]
allowedMethods: GET, POST, PUT, DELETE, OPTIONS
allowedHeaders: Authorization, Content-Type, X-Requested-With, X-XSRF-TOKEN
allowCredentials: true
maxAge: 3600 seconds
```

### Trust Assumptions
- **Deployment environment**: Traffic terminates at AWS ALB → nginx ingress within Kubernetes
  (ADR-0019). The CORS origin list lives in `config/nginx/cors-origins.conf` and changes only land
  via GitOps pull requests with two reviewers plus an approver from the platform team.
- **Reverse proxy hardening**: nginx overwrites (never appends) `Origin` and `X-Forwarded-*`
  headers, and we lint that config in CI. CloudTrail plus nginx audit logs capture every config
  change, and drift detection alerts if someone bypasses GitOps.
- **Configuration enforcement**: FluxCD continuously reconciles the ingress manifest and alerts on
  drift. Any manual change to ALB listeners or nginx ConfigMaps is blocked by IAM; Terraform plan
  and `kubeval` run in CI before merge.
- **Runtime monitoring**: AWS WAF rules and Datadog monitors flag unusual Origin headers
  (wildcards, IP literals, multiple values). Logs are reviewed weekly and alarms page on-call if
  spoofed Origins reach the app tier.
- **Residual trust**: Browser enforcement remains a defense-in-depth measure, but we assume modern
  browsers honor credentialed CORS. If instrumentation shows anomalies, we temporarily fall back to
  double-checked Origin validation within the Spring filter.

## 6. JWT Security Model

### Token Properties
| Property     | Value           | Rationale                                                  |
|--------------|-----------------|------------------------------------------------------------|
| Algorithm    | HMAC-SHA256     | Symmetric; no public key distribution needed               |
| Expiration   | 30 minutes      | Limits blast radius of stolen tokens; shorter idle windows |
| Storage      | HttpOnly cookie | Prevents XSS token theft                                   |
| Transmission | Cookie header   | Automatic browser inclusion                                |

### Token Claims
```json
{
  "sub": "username",
  "iat": 1701432000,
  "exp": 1701518400
}
```

### Security Controls
- Access tokens set with `Secure`, `HttpOnly`, and `SameSite=Lax` attributes and limited to a
  30-minute TTL.
- Secret key: 256-bit minimum, from environment variable
- Short-lived sessions remove the need to persist refresh tokens; future refresh flow deferred to
  ADR backlog.
- Logout clears cookie (no server-side blacklist yet)

#### Secret Key Lifecycle
- **Rotation cadence**: Primary signing key rotates every 90 days. Rollover uses two-slot storage
  (`ACTIVE`, `PREVIOUS`) so existing tokens remain valid for up to one hour while clients refresh.
- **Provisioning**: Keys are generated with `openssl rand -base64 64`, stored in Vault, and injected
  at deploy time via Kubernetes secrets. Only the platform team (2 people) can read/export keys.
- **Compromise response**: Immediately mark the compromised key as `REVOKED`, promote the
  standby key, generate a new standby, and invalidate all refresh sessions by clearing cookies and
  forcing logins.
- **Auditing & monitoring**: Access to the Vault path is logged and piped to the SIEM; we alert on
  unusual reads. Application logs emit metrics when signature verification fails so SOC can detect
  brute-force attempts or old key reuse.

### Known Limitations
- Token cannot be revoked before expiration
- Logout is client-side only (token remains valid)
- Future: Implement token blacklist for logout/revocation

## 7. Rate Limiting Strategy

### Endpoint Limits
| Endpoint Pattern     | Limit               | Window | Key           | Purpose                     |
|----------------------|---------------------|--------|---------------|-----------------------------|
| `/api/auth/login`    | 5/username & 100/IP | 60s    | Username + IP | Prevent credential stuffing |
| `/api/auth/register` | 3                   | 60s    | IP            | Prevent account spam        |
| `/api/v1/**`         | 100                 | 60s    | Username      | Fair resource allocation    |

### Implementation
- Algorithm: Token bucket (bucket4j)
- Storage: In-memory (Caffeine cache)
- Response: HTTP 429 with Retry-After header

**Account lockout**: After 5 failed login attempts for the same username within 15 minutes, the
account is locked for 15 minutes. Successful logins reset counters; admins can override locks via
audited support tooling. Lock/unlock events are emitted to the SIEM for alerting.

### Proxy Considerations
- **Configuration enforcement**: The nginx ingress ConfigMap that sets `X-Forwarded-For` lives in
  Terraform + Helm. CI runs `kube-linter` to ensure `proxy_set_header X-Forwarded-For $remote_addr`
  and `proxy_set_header X-Real-IP $remote_addr` are present. Config drift alerts fire via
  Kubernetes Audit logs if anyone patches the config outside GitOps.
- **Direct exposure safeguards**: If the service is temporarily exposed directly, the rate limiter
  falls back to the TCP remote address, and the AWS WAF enforces geo/IP reputation checks and
  client TLS verification before trusting the header.
- **Monitoring**: Structured logs capture every `X-Forwarded-For` value. A Datadog monitor alerts on
  multiple comma-separated IPs, private ranges, or `unknown`. These events generate on-call tickets.
- **Fallback behavior**: When the header is missing/invalid, we treat the request as untrusted,
  apply the most restrictive global rate limit, challenge the user (captcha/429), and log the event
  for manual review.

## 8. Per-User Data Isolation

### Database Schema
```sql
CREATE TABLE contacts (
    id BIGSERIAL PRIMARY KEY,
    contact_id VARCHAR(10) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    -- other columns
    UNIQUE(contact_id, user_id)
);
```

### Enforcement Layers
1. **Repository**: `findByIdAndUser(id, user)` methods
2. **Service**: Extracts user from SecurityContext
3. **Controller**: No user ID in request (derived from JWT)
4. **Database**: Foreign key constraint

### ADMIN Override
- Overrides now require `POST /api/v1/admin/query` with `{"includeAll": true}` in the JSON payload
  plus the `X-Admin-Override: true` header. Query-string toggles are deprecated and will be removed
  after **2026-02-01**.
- Requests must include CSRF tokens and are gated by `@PreAuthorize("hasRole('ADMIN')")` to reduce
  accidental leaks.
- Audit trail captures: actor user ID, role, timestamp, IP (from verified proxy header),
  resource type, filter params, record counts, and the business justification supplied in the
  request body. Logs live in the immutable audit index for 1 year with SOC-only read access.
- Alerting rules fire if an admin uses the override more than 3 times per hour or requests data for
  more than 1,000 records. Security reviews those alerts weekly.

## 9. Security Headers

| Header                  | Value                                                                                                                                                                                                | Purpose                     |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| Content-Security-Policy | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'self'; form-action 'self'; base-uri 'self'; object-src 'none'` | XSS mitigation              |
| Permissions-Policy      | `geolocation=(), microphone=(), camera=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()`                                                                                      | Feature restriction         |
| X-Content-Type-Options  | `nosniff`                                                                                                                                                                                            | MIME sniffing prevention    |
| X-Frame-Options         | `SAMEORIGIN`                                                                                                                                                                                         | Clickjacking prevention     |
| Referrer-Policy         | `strict-origin-when-cross-origin`                                                                                                                                                                    | Referrer leakage prevention |

## 10. Residual Risks

| Risk | Severity | Mitigation Status | Notes |
|------|----------|-------------------|-------|
| JWT token valid until expiration after logout | Medium | Accepted | Stateless design trade-off |
| X-Forwarded-For spoofing behind proxy | Medium | Documented | Proxy must sanitize |
| No email verification on registration | Low | Deferred | Future enhancement |

## 11. Security Testing

### Automated Testing
- **SAST**: CodeQL analysis in CI
- **SCA**: OWASP Dependency-Check in CI
- **API Fuzzing**: Schemathesis (30,000+ requests)
- **Unit Tests**: 1107 tests including security scenarios

### Planned Testing
- **DAST**: OWASP ZAP baseline scans land in Phase 5.5 (scheduled two sprints before the GA
  production launch). Release readiness requires one clean ZAP run in CI and one manual authenticated
  scan in staging. If Phase 5.5 slips, production launch is blocked; interim mitigations include
  WAF virtual patches and additional log review.
- **Penetration Testing**: Manual testing before production

## 12. References

- [ADR-0018: Authentication and Authorization Model](../adrs/ADR-0018-authentication-and-authorization-model.md)
- [ADR-0038: Authentication Implementation](../adrs/ADR-0038-authentication-implementation.md)
- [ADR-0039: Phase 5 Security and Observability](../adrs/ADR-0039-phase5-security-observability.md)
- [ADR-0043: HttpOnly Cookie Authentication](../adrs/ADR-0043-httponly-cookie-authentication.md)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [STRIDE Threat Model](https://docs.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats)
