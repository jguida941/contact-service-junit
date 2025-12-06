# ADR-0043: HttpOnly Cookie-Based Authentication

**Status:** Accepted | **Date:** 2025-12-01 | **Owners:** Justin Guida

**Related:** [ADR-0049 Spring Security 7 SPA CSRF](ADR-0049-spring-security-7-spa-csrf.md) (CSRF implementation details for Spring Boot 4.0+),
[ADR-0052 Production Auth System](ADR-0052-production-auth-system.md) (401/403 handling, JWT hardening)

> **Update (2025-12-03):** ADR-0052 Batch 1 enhanced 401 vs 403 handling. Frontend now differentiates: 401 triggers logout, 403 shows "Access Denied" without clearing session.

## Context

The CODEBASE_AUDIT.md identified a critical security vulnerability (FRONTEND-SEC-01): JWT tokens were stored in localStorage, making them accessible to any JavaScript code including potential XSS attacks. Additionally, user profile data was also stored in localStorage (FRONTEND-SEC-03).

**Problems with localStorage token storage:**
1. XSS attacks can steal tokens via `localStorage.getItem('auth_token')`
2. Any malicious script (including compromised dependencies) can access tokens
3. Tokens persist even after browser close, increasing theft window
4. No automatic expiration handling by browser

## Decision

Migrate from localStorage-based Bearer token authentication to HttpOnly cookie-based authentication.

### Backend Changes

1. **AuthController.java**:
* Login/register endpoints now set HttpOnly cookies instead of returning tokens in response body
* Logout endpoint clears the auth cookie
* Cookie attributes: `HttpOnly`, `Secure` (in prod), `SameSite=Lax`, `Path=/`

2. **JwtAuthenticationFilter.java**:
* Now extracts JWT from cookie first, falls back to Authorization header
* Maintains backward compatibility for API clients using Bearer tokens

3. **Token use separation (update)**:
* Tokens include `token_use` claim:
  * `session`: browser flow, fingerprint-bound via cookie (`fph` claim required)
  * `api`: programmatic flow, no fingerprint claim, header-only; filter rejects any API token that carries `fph`
* `/api/auth/api-token` issues API tokens without setting cookies; `/api/auth/login|register|refresh` issue session tokens with fingerprint cookies.

3. **application.yml**:
- Added cookie security configuration
- `app.auth.cookie.secure` property (env: `APP_AUTH_COOKIE_SECURE`) controls the auth cookie Secure flag:
- **Dev/Test**: Defaults to `false` via `application.yml` so localhost HTTP flows keep working.
- **Prod**: Mandatory. Operators must set `APP_AUTH_COOKIE_SECURE=true` (or `COOKIE_SECURE=true`, which the prod profile maps to the same setting). There is no fallback in prod and rollout checklists treat a missing value as a deployment blocker.

### Frontend Changes

1. **api.ts**:
- Removed localStorage token storage
- Added `credentials: 'include'` to all fetch calls
- User info stored in sessionStorage (typically clears on tab close; browser session-restore
  and bfcache scenarios may delay cleanup)
- `isAuthenticated()` now checks for user session, not token

2. **useProfile.ts**: Changed from localStorage to sessionStorage

3. **SettingsPage.tsx**: Updated to clear sessionStorage

### Credentialed CORS Requirements

Setting `credentials: 'include'` forces the backend to explicitly allow credentialed
cross-origin requests. Per [ADR-0019](ADR-0019-deployment-and-packaging.md) and the reverse proxy
deploy docs, every environment (local, staging, prod) must configure:

```
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: https://spa.contact-suite.example (no wildcards)
Access-Control-Allow-Headers: Authorization, Content-Type, X-XSRF-TOKEN, X-Request-ID
Access-Control-Expose-Headers: X-Request-ID, X-Trace-ID
```

**Operator checklist**
- Keep the SPA origin list in source control and require two-person review for changes
- Re-deploy reverse proxy + Spring `CorsRegistry` on the same commit to avoid drift
- Verify the `OPTIONS` preflight includes `Access-Control-Allow-Credentials: true`
- Monitor for blocked credentialed requests in CDN/WAF logs and alert on violations
- Document the mapping in release checklists so backend/frontend deployments stay aligned

## Cookie Attributes Rationale

| Attribute | Value          | Reason                                             |
|-----------|----------------|----------------------------------------------------|
| HttpOnly  | true           | Prevents JavaScript access, blocks XSS token theft |
| Secure    | true (prod)    | Ensures cookie only sent over HTTPS                |
| SameSite  | Lax            | Defense-in-depth (not sole CSRF protection)        |
| Path      | /              | Cookie available to all endpoints                  |
| Max-Age   | JWT expiration | Browser auto-clears expired cookies                |

## CSRF Protection

**Important**: Cookie-based authentication requires explicit CSRF protection. SameSite=Lax alone is NOT sufficient.

### Implementation

1. **Backend**: Spring Security's `CookieCsrfTokenRepository.withHttpOnlyFalse()` sets `XSRF-TOKEN` cookie
2. **Frontend**: Reads `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` header on mutating requests
3. **Endpoint**: `/api/auth/csrf-token` returns token for initial page load

### CSRF-Exempt Endpoints

Only `/api/auth/login` and `/api/auth/register` are exempt (user has no CSRF token yet).
All other `/api/**` endpoints require valid CSRF token.

## Consequences

### Positive

- **XSS Protection**: Tokens completely inaccessible to JavaScript
- **Automatic Handling**: Browser manages cookie lifecycle
- **Session Scoping**: sessionStorage typically clears on tab close (subject to browser restore
  features)
- **Backward Compatibility**: API clients can still use Authorization header

### Negative

- **CORS Complexity**: Must use `credentials: 'include'` on all requests
- **CSRF Overhead**: Frontend must read CSRF cookie and send X-XSRF-TOKEN header
- **Debugging Harder**: Can't inspect JWT in DevTools (by design)
- **Cross-Domain Issues**: Cookies don't work across different domains

### Neutral

- **Same Security Model**: Backend JWT validation unchanged
- **Test Updates**: Required test modifications for cookie assertions

## Migration Notes

### 1. Legacy localStorage Token Cleanup
- Deployment enablement job sets `X-Legacy-Token: purge` on every response when a request
  still presents the deprecated `auth_token` localStorage value. The SPA listens for that
  header and deletes the entry immediately.
- Authentication service invalidates all outstanding bearer tokens issued before
  **2025-12-05** (the migration window start) so even users that keep the tab open are prompted
  to re-authenticate and receive the HttpOnly cookie.
- Customer support playbook instructs users to refresh or log out/in if they report repeated
  401s; support articles link to this ADR.

### 2. Frontend Bootstrap Migration Handler
- On boot, the SPA checks `localStorage.auth_token`. If present, it silently calls the
  `/api/auth/login` endpoint with the legacy token in the `Authorization` header exactly once.
  When the backend returns `Set-Cookie: auth_token=...`, the SPA deletes the localStorage entry
  and caches the profile in sessionStorage.
- If the exchange fails (token expired/invalid), the SPA removes the localStorage token, clears
  TanStack caches, and shows a "Session expired — please sign in again" message.
- A run-once vitest ensures the bootstrap hook deletes the stored token even when cookies are
  blocked so we do not regress.

### 3. Deprecation Timeline
- **2025-12-05**: Migration window opens with dual support (Bearer header + cookie). Release tag:
  `v5.5.0-migration`.
- **2025-12-19**: Scheduled job removes any lingering localStorage tokens by issuing a logout
  broadcast to connected clients.
- **2026-01-05**: Enforcement date — Authorization header tokens without CSRF cookies are rejected
  with `401 LegacyTokenDisabled`. Backend logging verifies 0 requests per hour before closing the
  window.
- Implementation steps, manual test scripts, and on-call alerts are tracked in
  `docs/CI-CD/ci_cd_plan.md#phase-5-5-cookie-rollout`.

### 4. Rollback and Contingency Plan
If 401 `LegacyTokenDisabled` errors spike unexpectedly:

1. **Immediate Response** (< 15 min):
   - Set feature flag `LEGACY_TOKEN_ALLOWED=true` in environment config
   - This re-enables Authorization header fallback without a deploy
   - Monitor error rates return to baseline

2. **Root Cause Analysis**:
   - Check CORS misconfiguration (missing `Access-Control-Allow-Credentials`)
   - Verify SPA clients have updated to cookie-based auth
   - Review `LegacyTokenUsed` metrics to identify remaining legacy clients

3. **Extended Rollback** (if needed):
   - Revert to `v5.5.0-migration` tag which supports dual-mode
   - Extend deprecation timeline by 30 days
   - Notify affected customers via support channels

4. **Timeline Flexibility**:
   - Dates are targets, not hard deadlines
   - Enforcement can be deferred via `JWT_LEGACY_CUTOFF_DATE` env var
   - Enterprise customers may request extended support windows

## References

- OWASP JWT Best Practices: https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
- SameSite Cookie Attribute: https://web.dev/samesite-cookies-explained/
- CODEBASE_AUDIT.md: FRONTEND-SEC-01, FRONTEND-SEC-03
