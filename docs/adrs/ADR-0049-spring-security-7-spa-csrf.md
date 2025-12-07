# ADR-0049: Spring Security 7 SPA CSRF Configuration

**Status**: Accepted | **Date**: 2025-12-03 | **Owners**: Justin Guida

**Related**: [ADR-0043 HttpOnly Cookie Authentication](ADR-0043-httponly-cookie-authentication.md), [SecurityConfig.java](../../src/main/java/contactapp/security/SecurityConfig.java)

## Context

After upgrading to Spring Boot 4.0.0 (Spring Security 7.0.1), the React SPA experienced persistent 403 Forbidden errors on all state-changing requests (POST, PUT, DELETE). The root cause was a breaking change in how Spring Security 6+ handles CSRF token validation for SPAs.

### The Problem

1. **Deferred Token Loading**: Spring Security 6+ defers CSRF token loading by default. The `XSRF-TOKEN` cookie is only set when a state-changing request triggers it—but SPAs need the cookie *before* their first POST.

2. **BREACH Protection Mismatch**: The default `XorCsrfTokenRequestAttributeHandler` XOR-encodes tokens for BREACH attack protection. When an SPA reads the raw token from the `XSRF-TOKEN` cookie and sends it in the `X-XSRF-TOKEN` header, the server compares the raw value against the XOR-masked value—causing validation failure.

3. **Custom Handler Complexity**: The existing `SpaCsrfTokenRequestHandler` (based on Spring Security 5.8 migration docs) attempted to handle both raw and masked tokens, but didn't account for all edge cases in Spring Security 7's filter chain ordering.

### Symptoms Observed

```
[Error] Failed to load resource: the server responded with a status of 403 (Forbidden) (tasks, line 0)
[Error] Failed to load resource: the server responded with a status of 403 (Forbidden) (contacts, line 0)
```

All CRUD operations failed while GET requests (which don't require CSRF) worked normally.

## Decision

Use `SpaCsrfTokenRequestHandler` with custom cookie configuration for full control over CSRF token handling:

```java
final CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
csrfTokenRepository.setCookieCustomizer(cookie -> cookie
        .sameSite("Lax")
        .secure(secureSessionCookie));

final SpaCsrfTokenRequestHandler csrfHandler = new SpaCsrfTokenRequestHandler();

http.csrf(csrf -> csrf
    .ignoringRequestMatchers(CSRF_IGNORED_MATCHERS)
    .csrfTokenRepository(csrfTokenRepository)
    .csrfTokenRequestHandler(csrfHandler));
```

> **Note**: Spring Security 7's `.spa()` method provides similar functionality, but we use explicit configuration to ensure `SameSite=Lax` and environment-specific `Secure` flag are applied to the CSRF cookie.

### Additional Fixes Applied

1. **CsrfCookieFilter**: Added a filter that eagerly loads the CSRF token on every request, ensuring the `XSRF-TOKEN` cookie is set before the SPA's first POST:

```java
public final class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();  // Forces deferred token to load
        }
        filterChain.doFilter(request, response);
    }
}
```

2. **Cookie Customization Preserved**: The `CookieCsrfTokenRepository` still uses custom `SameSite=Lax` and environment-appropriate `Secure` flag settings.

## How `.spa()` Works

The `.spa()` method (introduced in Spring Security 6.4, refined in 7.0) internally:

1. Configures `CookieCsrfTokenRepository.withHttpOnlyFalse()` so JavaScript can read the token
2. Uses `SpaCsrfTokenRequestHandler` which properly delegates token resolution:
   - Accepts raw tokens from `X-XSRF-TOKEN` header (for SPAs)
   - Accepts XOR-masked tokens from `_csrf` form parameter (for server-rendered pages)
3. Maintains BREACH protection for any server-rendered responses while allowing raw token validation for API calls

## Consequences

### Positive

- **Correct Token Handling**: `SpaCsrfTokenRequestHandler` accepts raw tokens from headers (SPAs) while preserving XOR masking for form parameters (SSR)
- **BREACH Protected**: Server-rendered responses still get XOR-masked tokens; SPAs use raw tokens safely because they're not embedded in HTML
- **Custom Cookie Control**: Explicit configuration allows setting `SameSite=Lax` and environment-specific `Secure` flag
- **Spring Security Recommended**: This is the official pattern from Spring Security's migration guide

### Negative

- **Spring Security 7+ Required**: The `.spa()` method is not available in older versions; projects on Spring Security 5.x/6.x need the manual workaround
- **Filter Order Dependency**: `CsrfCookieFilter` must run after `BasicAuthenticationFilter` for proper timing

### Neutral

- **Cookie Repository Still Customizable**: Can still set `SameSite`, `Secure`, and other cookie attributes
- **Ignored Matchers Unchanged**: `/api/auth/**` endpoints remain CSRF-exempt per ADR-0043

## Files Changed

| File | Change |
|------|--------|
| `SecurityConfig.java` | Uses `SpaCsrfTokenRequestHandler` with custom cookie configuration |
| `CsrfCookieFilter.java` | Added to eagerly load CSRF token on every request |
| `SpaCsrfTokenRequestHandler.java` | Custom handler that accepts raw tokens from headers (SPA) and XOR-masked from forms (SSR) |

## References

- [Spring Security CSRF Documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Stack Overflow: CSRF protection not working with Spring Security 6](https://stackoverflow.com/questions/74447118/csrf-protection-not-working-with-spring-security-6)
- [GitHub Issue #12869: XorCsrfTokenRequestAttributeHandler rejects valid CSRF token](https://github.com/spring-projects/spring-security/issues/12869)
- [Spring Security 5.8 Migration Guide](https://docs.spring.io/spring-security/reference/5.8/migration/servlet/exploits.html)
