package contactapp.security;

import contactapp.api.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that intercepts requests to validate JWT tokens.
 *
 * <p>Extracts the token from either:
 * <ol>
 *   <li>HttpOnly cookie (preferred, set by login/register endpoints)</li>
 *   <li>Authorization header (fallback for API clients)</li>
 * </ol>
 *
 * <p>Cookie-based auth is preferred for browser clients as it protects against XSS token theft.
 * Header-based auth is supported for programmatic API access (e.g., CI/CD, scripts).
 *
 * <p>When token fingerprinting is enabled (ADR-0052 Phase C), the filter also verifies that
 * the fingerprint cookie matches the hash stored in the JWT. Set {@code jwt.require-fingerprint=true}
 * to reject tokens without fingerprint claims (recommended for new deployments).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenFingerprintService fingerprintService;
    private final boolean requireFingerprint;

    public JwtAuthenticationFilter(
            final JwtService jwtService,
            final UserDetailsService userDetailsService,
            final TokenFingerprintService fingerprintService,
            @Value("${jwt.require-fingerprint:false}")
            final boolean requireFingerprint
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.fingerprintService = fingerprintService;
        this.requireFingerprint = requireFingerprint;
    }

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
    ) throws ServletException, IOException {
        // Try to extract JWT from cookie first, then fall back to Authorization header
        final String jwt = extractJwtFromCookie(request)
                .or(() -> extractJwtFromHeader(request))
                .orElse(null);

        // Skip if no JWT found
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String username = jwtService.extractUsername(jwt);

            final Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

            if (username != null && existingAuth == null) {
                final UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    // Verify fingerprint if present in token (ADR-0052 Phase C)
                    if (!verifyFingerprintIfPresent(jwt, request)) {
                        logger.warn("Token fingerprint mismatch");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    final UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is invalid - continue without authentication
            // Security context remains empty, protected endpoints will return 401
            // Log constant message to avoid leaking validation details
            logger.debug("JWT validation failed");
            logger.trace("JWT validation error details", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from the auth cookie.
     *
     * @param request the HTTP request
     * @return Optional containing the JWT if found in cookie
     */
    private java.util.Optional<String> extractJwtFromCookie(final HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AuthController.AUTH_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .findFirst();
    }

    /**
     * Extracts JWT token from the Authorization header.
     *
     * @param request the HTTP request
     * @return Optional containing the JWT if found in header
     */
    private java.util.Optional<String> extractJwtFromHeader(final HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return java.util.Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }
        return java.util.Optional.empty();
    }

    /**
     * Verifies the token fingerprint if the JWT contains a fingerprint claim.
     *
     * <p>When {@code jwt.require-fingerprint=true}, tokens without fingerprint claims
     * are rejected. When false (default), such tokens are accepted for backwards
     * compatibility during migration.
     *
     * @param jwt the JWT token
     * @param request the HTTP request (to extract fingerprint cookie)
     * @return true if fingerprint is valid (or not required), false if mismatch or missing when required
     */
    private boolean verifyFingerprintIfPresent(final String jwt, final HttpServletRequest request) {
        final TokenUse tokenUse = jwtService.extractTokenUse(jwt);
        final TokenUse effectiveUse = tokenUse != null ? tokenUse : TokenUse.SESSION;

        // Programmatic API tokens are header-based and intentionally not fingerprint-bound
        if (TokenUse.API.equals(effectiveUse)) {
            final String hash = jwtService.extractFingerprintHash(jwt);
            if (hash != null) {
                logger.warn("Token rejected: API token contained unexpected fingerprint claim");
                return false;
            }
            return true;
        }

        final String expectedHash = jwtService.extractFingerprintHash(jwt);

        // No fingerprint claim in token
        if (expectedHash == null) {
            if (requireFingerprint) {
                logger.warn("Token rejected: fingerprint required but not present in JWT");
                return false;
            }
            // Backwards compatible mode - accept tokens without fingerprint
            return true;
        }
        if (expectedHash.isEmpty()) {
            logger.warn("JWT fingerprint claim is present but empty");
            return false;
        }

        // Token has fingerprint - must verify against cookie
        // Uses service method that checks both __Secure-Fgp (HTTPS) and Fgp (HTTP dev)
        final String fingerprint = fingerprintService.extractFingerprint(request).orElse(null);
        if (fingerprint == null) {
            // Token requires fingerprint but cookie is missing
            return false;
        }

        return fingerprintService.verifyFingerprint(fingerprint, expectedHash);
    }
}
