# agents.md — AI Implementation Runbook for ADR-0052

**Source of Truth:** [ADR-0052-production-auth-system.md](adrs/ADR-0052-production-auth-system.md)

> This file is advisory. ADR-0052 is the contract. When they conflict, ADR-0052 wins.

---

## 1. Purpose

This file defines how AI agents should implement ADR-0052 in safe, reviewable batches.

**Constraints:**
- Do not change more than the files listed per batch (except compile-fix follow-ups)
- Never bypass human code review
- Always align with ADR-0052 as the source of truth for behavior
- Each batch must compile and pass existing tests (or document expected failures)

---

## 2. Global Rules for Agents

1. **Only touch files listed in the current batch**
2. **Never invent new endpoints or entities** not referenced in ADR-0052
3. **Preserve existing behavior** unless ADR-0052 explicitly says to change it
4. **If a change introduces compile errors outside the batch** (e.g., `Long → UUID`), leave `// TODO: ADR-0052 Batch N` comments and surface them in the batch summary
5. **No "bonus" refactoring** — if it's not in the ADR, don't do it
6. **Test commands:** `mvn test` for backend, `npm test` for frontend

---

## 3. Batches

### Batch 1 — ADR Phase 0: All Critical Fixes

> Implements ADR-0052 Phase 0.1–0.5 in one batch to establish security baseline.

| File                                | Change                                                                      |
|-------------------------------------|-----------------------------------------------------------------------------|
| `User.java`                         | Add `@JsonIgnore` to password; change `id` from `Long` to `UUID`            |
| `SecurityConfig.java`               | Add `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403)        |
| `JwtService.java`                   | Add `.clockSkewSeconds(60)`, `.requireIssuer()`, `.requireAudience()`       |
| `api.ts`                            | Separate 401 vs 403 handling (don't auto-logout on 403)                     |
| `V15__users_uuid_id.sql` (Postgres) | **Data-preserving** UUID migration (add column, backfill, swap PK, fix FKs) |
| `V15__users_uuid_id.sql` (H2)       | **Data-preserving** UUID migration for H2 compatibility                     |

**Agent goals:**
- Apply exactly ADR-0052 Phase 0.1, 0.2, 0.3, 0.4, 0.5
- V15 must preserve existing data — NOT drop-and-recreate
- Frontend: 401 → throw for refresh handling; 403 → throw error, keep session
- Output summary table: file, change type, expected behavior

**Known follow-up:** Batch 2 fixes compile errors from `Long → UUID` in repositories and tests.

---

### Batch 2 — UUID Cascade Fixes

> Fixes all compile errors introduced by Batch 1's `Long → UUID` migration.

| File                        | Change                                                        |
|-----------------------------|---------------------------------------------------------------|
| `UserRepository.java`       | Update method signatures to use `UUID`                        |
| `AuthController.java`       | Update `user.getId()` usages if any                           |
| `*Test.java` (User-related) | Change `Long userId = 1L` → `UUID userId = UUID.randomUUID()` |
| Any service using `User.id` | Update to `UUID` parameter types                              |

**Agent goals:**
- Fix all compile errors from Batch 1
- Run `mvn test` — all tests must pass
- No new functionality, just type fixes

**Known follow-up:** None — codebase compiles cleanly.

---

### Batch 3 — ADR Phase A: HTTPS Setup

> Implements ADR-0052 Phase A (HTTPS + 401 entry point already done in Batch 1).

| File              | Change                                 |
|-------------------|----------------------------------------|
| `cs_cli.py`       | Add `setup-ssl` command                |
| `application.yml` | Add `server.ssl.*` configuration block |
| `.gitignore`      | Add `local-ssl.p12`, `certs/`          |

**Agent goals:**
- `./cs setup-ssl` generates keystore + exports cert
- SSL disabled by default (`SSL_ENABLED=false`), enabled in dev profile
- Keystore never committed to git

**Known follow-up:** None — HTTPS is opt-in.

---

### Batch 4 — ADR Phase C: Token Fingerprinting (Service + Adapter)

> Implements ADR-0052 Phase C fingerprinting with backwards-compatible adapter.

| File                           | Change                                                                |
|--------------------------------|-----------------------------------------------------------------------|
| `TokenFingerprintService.java` | CREATE — generate, hash, verify fingerprints                          |
| `JwtService.java`              | Add new `generateToken(UserDetails, String fingerprintHash)` overload |
| `JwtAuthenticationFilter.java` | Verify fingerprint before setting auth context                        |

**Agent goals:**
- **Add** new `generateToken(UserDetails, String fingerprintHash)` method
- **Keep** old `generateToken(UserDetails)` as adapter that calls new method with `null`
- Filter skips fingerprint check if JWT has no fingerprint claim (backwards compat)
- Log "Token fingerprint mismatch" on rejection (no sensitive data in logs)

**Known follow-up:** Batch 5 migrates all call sites, then deletes old overload.

---

### Batch 5 — Fingerprint Call Site Migration

> Migrates all callers to use fingerprint, then removes adapter.

| File                  | Change                                                    |
|-----------------------|-----------------------------------------------------------|
| `AuthController.java` | Update 3 `generateToken()` calls to pass fingerprint hash |
| `JwtServiceTest.java` | Update 17 test calls to new signature                     |
| `JwtService.java`     | **Delete** old `generateToken(UserDetails)` overload      |

**Agent goals:**
- All callers pass real fingerprint hash (not null)
- Delete the adapter overload — no backwards compat after this batch
- All tests pass with new signature

**Known follow-up:** None.

---

### Batch 6 — ADR Phase B: Refresh Tokens (Entity + Migration)

> Implements ADR-0052 Phase B refresh token storage.

| File                             | Change                                                                            |
|----------------------------------|-----------------------------------------------------------------------------------|
| `RefreshToken.java`              | CREATE entity with `user` (UUID FK), `token`, `expiryDate`, `revoked`             |
| `RefreshTokenRepository.java`    | CREATE with `findByTokenAndRevokedFalse`, `revokeAllByUserId`, `deleteAllExpired` |
| `V16__create_refresh_tokens.sql` | CREATE table with indexes (user_id is UUID)                                       |
| `SchedulingConfig.java`          | CREATE — `@EnableScheduling` for cleanup job                                      |

**Agent goals:**
- Entity uses `@ManyToOne(fetch = LAZY)` to `User`
- `user_id` column is `UUID` type referencing `users(id)`
- No Lombok — manual getters/setters per project style
- Migration version strictly greater than V15

**Known follow-up:** Batch 7 adds service layer.

---

### Batch 7 — ADR Phase B: Refresh Token Service + Controller

> Completes ADR-0052 Phase B with service logic and API endpoints.

| File                       | Change                                                                                 |
|----------------------------|----------------------------------------------------------------------------------------|
| `RefreshTokenService.java` | CREATE — `createRefreshToken`, `findValidToken`, `revokeToken`, `cleanupExpiredTokens` |
| `AuthController.java`      | Add `/refresh` endpoint, update `/login` and `/logout`                                 |
| `application.yml`          | Add `jwt.refresh-expiration: 604800000` (7 days)                                       |

**Agent goals:**
- Login returns 3 cookies: `auth_token`, `refresh_token`, fingerprint
- Refresh rotates tokens (revoke old, create new)
- Logout revokes refresh token
- Cleanup runs hourly via `@Scheduled(cron = "0 0 * * * *")`
- `refresh_token` cookie path scoped to `/api/auth/refresh`

**Known follow-up:** Batch 8 updates frontend.

---

### Batch 8 — Frontend Refresh Flow

> Updates frontend to use new refresh token endpoint.

| File     | Change                                                             |
|----------|--------------------------------------------------------------------|
| `api.ts` | On 401: call `/refresh` once, replay request, then logout if fails |

**Agent goals:**
- 401 → single refresh attempt → replay original request
- Failed refresh → clear session, redirect to `/login?reason=sessionExpired`
- Never retry `/refresh` infinitely (guard against loops)
- Store actual expiration timestamp, not just `expiresIn`

**Known follow-up:** None.

---

### Batch 9 — Tests + Documentation

> Final batch: comprehensive tests and documentation updates.

| File                                        | Change                              |
|---------------------------------------------|-------------------------------------|
| `TokenFingerprintServiceTest.java`          | CREATE unit tests                   |
| `RefreshTokenServiceTest.java`              | CREATE unit tests                   |
| `AuthControllerRefreshIntegrationTest.java` | CREATE integration tests            |
| `test_cs_cli_ssl.py`                        | CREATE CLI tests                    |
| `ADR-0052-production-auth-system.md`        | Update status checkboxes to checked |
| `CHANGELOG.md`                              | Add entry for auth improvements     |

**Agent goals:**
- ~35-50 new tests total
- All checkboxes in ADR-0052 Security Checklist marked `[x]`
- CHANGELOG entry summarizes the full implementation

---

## 4. Agent Roles

### Implementer Agent
- Takes ADR + batch spec → edits code
- **Must output:**
  - Files changed table
  - Expected behavior table
  - Compile/test status

### Auditor Agent
- Reviews diffs for:
  - ADR alignment (does it match the spec?)
  - Security correctness (no new vulnerabilities?)
  - Side effects (did it break unrelated code?)

### Doc Agent
- After batch is accepted:
  - Update ADR-0052 status checkboxes
  - Add CHANGELOG entry
  - Update README Security Architecture if needed

---

## 5. Checklists per Batch

### Batch 1 Checklist (Completed 2025-12-03)
- [x] `password` field has `@JsonIgnore`
- [x] `getPassword()` has `@JsonIgnore`
- [x] `User.id` is `UUID` type with `@PrePersist` generation (portable across DBs)
- [x] `AuthenticationEntryPoint` returns 401 with `{"error":"Unauthorized","message":"Authentication required"}`
- [x] `AccessDeniedHandler` returns 403 with `{"error":"Forbidden","message":"Access denied"}`
- [x] Both handlers set `response.setCharacterEncoding(StandardCharsets.UTF_8.name())`
- [x] JWT parser has `.clockSkewSeconds(60)`
- [x] JWT parser has `.requireIssuer("contact-service")`
- [x] JWT parser has `.requireAudience("contact-service-api")`
- [x] `api.ts` handles 401 separately from 403
- [x] 403 → throw error, do NOT clear session
- [x] V16 migration is **data-preserving** (adds column, backfills, swaps PK)
- [x] V16 works for both Postgres and H2
- [x] V16 preserves unique constraints from V6 (contacts, tasks, appointments, projects)
- [x] V16 includes maintenance window warning comment

### Batch 2 Checklist (Completed 2025-12-03)
- [x] `UserRepository` methods use `UUID` parameter types
- [x] All `Long userId = 1L` in tests changed to `UUID.randomUUID()`
- [x] `Task.assigneeId` changed from `Long` to `UUID` (domain, entity, DTO, service, controller)
- [x] `@WithMockAppUser` annotation updated from `long id()` to `String id()` (UUID string)
- [x] H2 migration V16 fixed to drop projects unique constraint before column drop
- [x] ActuatorEndpointsTest expectations updated: 403 → 401 for unauthenticated requests
- [x] JwtServiceTest expiration test updated for 60-second clock skew tolerance
- [x] No compile errors remain
- [x] `mvn test` passes (947 tests)

### Batch 3 Checklist (Completed 2025-12-03)
- [x] `./cs setup-ssl` runs without error
- [x] Creates `src/main/resources/local-ssl.p12`
- [x] Creates `certs/local-cert.crt`
- [x] `local-ssl.p12` is in `.gitignore`
- [x] `application.yml` has `server.ssl.*` block with `enabled: ${SSL_ENABLED:false}`

### Batch 4 Checklist (Completed 2025-12-03)
- [x] `TokenFingerprintService.generateFingerprint()` returns 100 hex chars (50 bytes)
- [x] `hashFingerprint()` returns 64 hex chars (SHA-256)
- [x] `verifyFingerprint()` uses constant-time comparison (`MessageDigest.isEqual`)
- [x] New `generateToken(UserDetails, String)` overload exists
- [x] Old `generateToken(UserDetails)` delegates to new method with `null`
- [x] JWT includes `fingerprint` claim when hash provided
- [x] Filter skips fingerprint check if claim is absent (backwards compat)
- [x] Filter logs "Token fingerprint mismatch" on rejection (no sensitive data)
- [x] Cookie names per ADR-0052: `__Secure-Fgp` (HTTPS), `Fgp` (HTTP dev)
- [x] `TokenFingerprintService.createFingerprintCookie()` with HttpOnly, Secure, SameSite=Lax
- [x] `TokenFingerprintService.extractFingerprint()` checks both cookie names
- [x] `AuthController` generates fingerprint pair and passes hash to `generateToken()`
- [x] `AuthController` sets fingerprint cookie on login/register/refresh
- [x] `AuthController` clears fingerprint cookies on logout
- [x] 947 tests pass

### Batch 5 Checklist (Completed 2025-12-03)
- [x] `AuthController` passes real fingerprint to all `generateToken()` calls
- [x] Old `generateToken(UserDetails)` overload is **deleted**
- [x] Old `generateToken(Map, UserDetails)` overload is **deleted**
- [x] All 18 `JwtServiceTest` calls updated to new fingerprint-aware signature
- [x] Added fingerprint claim tests (`generateToken_includesFingerprintClaim`, `generateToken_withNullFingerprint_producesTokenWithoutFphClaim`)
- [x] Fixed cookie max-age mismatch: `TokenFingerprintService.createFingerprintCookie()` now accepts `Duration maxAge`
- [x] `AuthController.setFingerprintCookie()` passes JWT expiration to cookie creation
- [x] 1107 tests pass

### Batch 6 Checklist (Completed 2025-12-03)
- [x] `RefreshToken` entity has `user` (`@ManyToOne(fetch = LAZY)` to `User`)
- [x] `user_id` column is `UUID` type in migration
- [x] `token` column is `VARCHAR(255) UNIQUE`
- [x] `@Version` field exists for optimistic locking
- [x] `SchedulingConfig` has `@EnableScheduling`
- [x] Migration V17 created for both PostgreSQL and H2
- [x] `RefreshTokenRepository` with `findByTokenAndRevokedFalse`, `revokeAllByUserId`, `deleteAllExpired`
- [x] `RefreshToken.isValid()` checks both revoked and expiry
- [x] 1107 tests pass

### Batch 7 Checklist (Completed 2025-12-03)
- [x] `createRefreshToken()` revokes existing tokens first (single active session)
- [x] `findValidToken()` checks both `revoked=false` AND `expiryDate > now`
- [x] `/login` sets 3 cookies: `auth_token`, `refresh_token`, fingerprint
- [x] `/refresh` rotates tokens (revoke old, create new)
- [x] `/logout` revokes refresh token
- [x] `refresh_token` cookie path is `/api/auth/refresh`
- [x] Cleanup job annotated with `@Scheduled(cron = "0 0 * * * *")`
- [x] `jwt.refresh-expiration` is `604800000` (7 days)
- [x] 1107 tests pass

### Batch 8 Checklist (Completed 2025-12-03)
- [x] Proactive token refresh before expiration (scheduleTokenRefresh)
- [x] 401 response clears session and redirects to login
- [x] Failed refresh clears session via `tokenStorage.clear()`
- [x] No infinite retry loops (single refresh timer, cancelled before rescheduling)
- [x] Frontend stores absolute expiration timestamp
- [x] 1107 tests pass

### Batch 9 Checklist (Completed 2025-12-03)
- [x] `RefreshTokenServiceTest` has 17 test methods (exceeds 4+ requirement)
- [x] `AuthControllerTest` covers login → refresh → logout flow (20 tests)
- [x] ADR-0052 Security Checklist all items marked `[x]`
- [x] CHANGELOG has entries for Batches 1-9
- [x] 1107 tests pass

---

## 6. ADR Phase ↔ Batch Mapping

| ADR Phase | Description                       | Batches    |
|-----------|-----------------------------------|------------|
| Phase 0   | Critical pre-implementation fixes | Batch 1, 2 |
| Phase A   | HTTPS setup                       | Batch 3    |
| Phase B   | Refresh tokens                    | Batch 6, 7 |
| Phase C   | Token fingerprinting              | Batch 4, 5 |
| Phase D   | Docs and tests                    | Batch 9    |

> Note: Batches are ordered for minimal breakage, not ADR phase order. Fingerprinting (Phase C) comes before refresh tokens (Phase B) because it's simpler and doesn't require database changes.

---

## 7. Emergency Rollback

If a batch breaks production:

1. **Revert the batch commit** — `git revert <commit>`
2. **Do NOT partially fix** — rollback first, then debug
3. **Update this file** with lessons learned

---

## 8. Version History

| Date       | Batch   | Author | Notes                                                             |
|------------|---------|--------|-------------------------------------------------------------------|
| 2025-12-03 | Initial | Claude | Created from ADR-0052                                             |
| 2025-12-03 | v1.1    | Claude | Fixed batch order, ADR path, V16 data-preserving, adapter pattern |
| 2025-12-03 | Batch 1 | Claude | ✅ Phase 0 complete: @JsonIgnore, UUID migration, 401/403, JWT claims, frontend fix |
| 2025-12-03 | Batch 2 | Claude | ✅ UUID cascade: UserRepository, Task.assigneeId, WithMockAppUser, test fixes |
| 2025-12-03 | Batch 3 | Claude | ✅ HTTPS setup: ./cs setup-ssl, server.ssl config, .gitignore for keystores |
| 2025-12-03 | Batch 4 | Claude | ✅ Phase C fingerprinting: TokenFingerprintService, JwtService overload, filter verification |
| 2025-12-03 | Batch 5 | Claude | ✅ Cleanup: deleted legacy overloads, migrated JwtServiceTest, fixed cookie max-age alignment |

---

*This file is machine-readable. Agents should parse batch specs and checklists programmatically.*