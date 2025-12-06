package contactapp.api;

import contactapp.api.dto.AuthResponse;
import contactapp.api.dto.ErrorResponse;
import contactapp.api.dto.LoginRequest;
import contactapp.api.dto.RegisterRequest;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.security.JwtService;
import contactapp.security.RefreshToken;
import contactapp.security.RefreshTokenService;
import contactapp.security.Role;
import contactapp.security.TokenFingerprintService;
import contactapp.security.TokenUse;
import contactapp.security.User;
import contactapp.security.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations (login, registration, token refresh).
 *
 * <p>Provides endpoints at {@code /api/auth} for user authentication per ADR-0018 and ADR-0043.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/auth/login - Authenticate user, set HttpOnly cookie, return user info (200 OK)</li>
 *   <li>POST /api/auth/register - Register new user, set HttpOnly cookie, return user info (201 Created)</li>
 *   <li>POST /api/auth/refresh - Refresh token (sliding session), return new token (200 OK)</li>
 *   <li>POST /api/auth/logout - Clear auth cookie (204 No Content)</li>
 * </ul>
 *
 * <h2>Token Refresh (Sliding Session)</h2>
 * <p>The refresh endpoint allows extending sessions without re-authentication:
 * <ul>
 *   <li>Access tokens expire after 30 minutes (configurable via jwt.expiration)</li>
 *   <li>Refresh is allowed if token is valid OR expired within refresh window (default 5 min)</li>
 *   <li>Frontend should call /refresh proactively ~5 min before expiry</li>
 *   <li>If refresh fails (token too old), user must re-authenticate</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>These endpoints are publicly accessible (no JWT required). Authentication uses
 * HttpOnly, Secure, SameSite=Lax cookies to protect against XSS token theft.
 * The browser automatically includes the cookie on subsequent requests.
 *
 * @see LoginRequest
 * @see RegisterRequest
 * @see AuthResponse
 * @see JwtService
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "User authentication and registration")
public class AuthController {

    /** Cookie name for JWT token storage. */
    public static final String AUTH_COOKIE_NAME = "auth_token";

    private static final String COOKIE_PATH = "/";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenFingerprintService fingerprintService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.auth.cookie.secure:true}")
    private boolean secureCookie;

    /**
     * Creates a new AuthController with the required dependencies.
     *
     * @param authenticationManager Spring Security authentication manager
     * @param userRepository repository for user persistence
     * @param passwordEncoder encoder for password hashing (BCrypt)
     * @param jwtService service for JWT token generation
     * @param fingerprintService service for token fingerprinting (ADR-0052 Phase C)
     * @param refreshTokenService service for refresh token management (ADR-0052 Phase B)
     */
    public AuthController(
            final AuthenticationManager authenticationManager,
            final UserRepository userRepository,
            final PasswordEncoder passwordEncoder,
            final JwtService jwtService,
            final TokenFingerprintService fingerprintService,
            final RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.fingerprintService = fingerprintService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Exposes the CSRF token so the SPA can include it in state-changing requests.
     *
     * <p>{@link org.springframework.security.web.csrf.CookieCsrfTokenRepository CookieCsrfTokenRepository}
     * issues the {@code XSRF-TOKEN} cookie with
     * {@code HttpOnly=false}, and this endpoint returns the same token for double-submit
     * protection. Clients should call this endpoint before POST/PUT/PATCH/DELETE requests.
     *
     * @param csrfToken Spring Security CSRF token injected by the filter chain
     * @return a JSON payload containing the CSRF token value
     */
    @Operation(summary = "Fetch CSRF token for SPA clients")
    @ApiResponse(responseCode = "200", description = "CSRF token returned")
    @GetMapping("/csrf-token")
    public Map<String, String> csrfToken(final CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    /**
     * Authenticates a user and sets an HttpOnly cookie with the JWT token.
     *
     * @param request the login credentials
     * @param response HTTP response for setting the auth cookie
     * @return authentication response with user info (token in cookie, not body)
     * @throws BadCredentialsException if credentials are invalid
     */
    @Operation(summary = "Authenticate user and set auth cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(
            @Valid @RequestBody final LoginRequest request,
            final HttpServletResponse response) {
        // Authenticate via Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // Load user and generate token with fingerprint (ADR-0052 Phase C)
        final User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Generate fingerprint pair: raw for cookie, hash for JWT
        final TokenFingerprintService.FingerprintPair fingerprint = fingerprintService.generateFingerprintPair();
        final String token = jwtService.generateToken(user, fingerprint.hashed(), TokenUse.SESSION);

        // Create refresh token (ADR-0052 Phase B) - revokes existing tokens
        final RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Set HttpOnly cookies: auth token, refresh token, and fingerprint
        setAuthCookie(response, token, jwtService.getExpirationTime());
        setRefreshTokenCookie(response, refreshToken.getToken());
        setFingerprintCookie(response, fingerprint.raw());

        return new AuthResponse(
                null, // Token is in HttpOnly cookie, not response body
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationTime()
        );
    }

    /**
     * Issues a non-fingerprinted API token for programmatic clients (header-based auth).
     *
     * <p>Unlike the browser login flow, this endpoint does not set cookies and does not
     * include a fingerprint claim. Tokens are intended to be used via
     * {@code Authorization: Bearer <token>} only.</p>
     *
     * @param request the login credentials
     * @return authentication response with token in body
     */
    @Operation(summary = "Issue API token for programmatic clients (Authorization header)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API token issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/api-token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse apiToken(
            @Valid @RequestBody final LoginRequest request
    ) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        final User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // API tokens are header-only and intentionally omit fingerprint binding
        final String token = jwtService.generateToken(
                Map.of(),
                user,
                null,
                TokenUse.API
        );

        return new AuthResponse(
                token, // Token returned in body for header-based clients
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationTime()
        );
    }

    /**
     * Registers a new user and sets an HttpOnly cookie with the JWT token.
     *
     * @param request the registration data
     * @param response HTTP response for setting the auth cookie
     * @return authentication response with user info (token in cookie, not body)
     * @throws DuplicateResourceException if username or email already exists
     */
    @Operation(summary = "Register new user and set auth cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody final RegisterRequest request,
            final HttpServletResponse response) {
        // Check for existing username or email (use generic message to prevent enumeration)
        if (userRepository.existsByUsername(request.username())
                || userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Username or email already exists");
        }

        // Create new user with hashed password
        final User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.USER
        );

        // Save with race condition handling - database constraint catches concurrent inserts
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request inserted the same username/email
            throw new DuplicateResourceException("Username or email already exists");
        }

        // Generate token with fingerprint for immediate login (ADR-0052 Phase C)
        final TokenFingerprintService.FingerprintPair fingerprint = fingerprintService.generateFingerprintPair();
        final String token = jwtService.generateToken(user, fingerprint.hashed(), TokenUse.SESSION);

        // Create refresh token (ADR-0052 Phase B)
        final RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Set HttpOnly cookies: auth token, refresh token, and fingerprint
        setAuthCookie(response, token, jwtService.getExpirationTime());
        setRefreshTokenCookie(response, refreshToken.getToken());
        setFingerprintCookie(response, fingerprint.raw());

        return new AuthResponse(
                null, // Token is in HttpOnly cookie, not response body
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationTime()
        );
    }

    /**
     * Refreshes the JWT token using a valid refresh token (ADR-0052 Phase B).
     *
     * <p>Token rotation is performed on each refresh:
     * <ul>
     *   <li>Old refresh token is revoked</li>
     *   <li>New refresh token is created</li>
     *   <li>New access token is issued with rotated fingerprint</li>
     * </ul>
     *
     * <p>Frontend clients should call this endpoint when receiving a 401,
     * attempting a single refresh before redirecting to login.
     *
     * @param request HTTP request containing the refresh_token cookie
     * @param response HTTP response for setting new cookies
     * @return authentication response with refreshed token info
     */
    @Operation(summary = "Refresh JWT token using refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token expired or invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(
            final jakarta.servlet.http.HttpServletRequest request,
            final HttpServletResponse response) {
        // Extract refresh token from cookie
        final String refreshTokenValue = extractRefreshTokenFromCookies(request);
        if (refreshTokenValue == null) {
            throw new BadCredentialsException("No refresh token found");
        }

        // Atomically validate and revoke refresh token (prevents TOCTOU race condition)
        // Uses pessimistic locking so concurrent refresh requests serialize at DB level
        final RefreshToken oldRefreshToken = refreshTokenService.validateAndRevokeAtomically(refreshTokenValue)
                .orElseThrow(() -> new BadCredentialsException(
                        "Invalid or expired refresh token - please log in again"));

        final User user = oldRefreshToken.getUser();

        // Create new refresh token
        final RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

        // Generate new access token with rotated fingerprint (ADR-0052 Phase C)
        final TokenFingerprintService.FingerprintPair fingerprint = fingerprintService.generateFingerprintPair();
        final String newAccessToken = jwtService.generateToken(user, fingerprint.hashed(), TokenUse.SESSION);

        // Set new cookies: access token, refresh token, and fingerprint
        setAuthCookie(response, newAccessToken, jwtService.getExpirationTime());
        setRefreshTokenCookie(response, newRefreshToken.getToken());
        setFingerprintCookie(response, fingerprint.raw());

        return new AuthResponse(
                null, // Token is in HttpOnly cookie, not response body
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationTime()
        );
    }

    /**
     * Logs out the current user by revoking tokens and clearing cookies.
     *
     * <p>Performs complete session termination:
     * <ul>
     *   <li>Revokes the refresh token in the database (ADR-0052 Phase B)</li>
     *   <li>Clears the auth_token cookie</li>
     *   <li>Clears the refresh_token cookie</li>
     *   <li>Clears the fingerprint cookies (ADR-0052 Phase C)</li>
     * </ul>
     *
     * @param request HTTP request containing the refresh token cookie
     * @param response HTTP response for clearing cookies
     */
    @Operation(summary = "Logout current user")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            final jakarta.servlet.http.HttpServletRequest request,
            final HttpServletResponse response) {
        // Revoke refresh token if present (ADR-0052 Phase B)
        final String refreshTokenValue = extractRefreshTokenFromCookies(request);
        if (refreshTokenValue != null) {
            refreshTokenService.revokeToken(refreshTokenValue);
        } else {
            // Fallback: revoke all user tokens when cookie is missing (e.g., legacy path scoping)
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                refreshTokenService.revokeAllUserTokens(user);
            }
        }

        // Clear all auth cookies
        clearAuthCookie(response);
        clearRefreshTokenCookie(response);
        clearFingerprintCookies(response);
    }

    /**
     * Extracts the JWT token from request cookies.
     *
     * @param request the HTTP request
     * @return the token value or null if not found
     */
    private String extractTokenFromCookies(final jakarta.servlet.http.HttpServletRequest request) {
        final jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final jakarta.servlet.http.Cookie cookie : cookies) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the refresh token from request cookies.
     *
     * @param request the HTTP request
     * @return the refresh token value or null if not found
     */
    private String extractRefreshTokenFromCookies(final jakarta.servlet.http.HttpServletRequest request) {
        final jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final jakarta.servlet.http.Cookie cookie : cookies) {
                if (RefreshTokenService.REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Sets the authentication cookie with HttpOnly, Secure, and SameSite attributes.
     *
     * @param response the HTTP response
     * @param token the JWT token
     * @param expirationMs token expiration time in milliseconds
     */
    private void setAuthCookie(final HttpServletResponse response, final String token, final long expirationMs) {
        final ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path(COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clears the authentication cookie.
     *
     * @param response the HTTP response
     */
    private void clearAuthCookie(final HttpServletResponse response) {
        final ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path(COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Sets the refresh token cookie (ADR-0052 Phase B).
     * Cookie path is scoped to /api/auth so it's sent to both refresh and logout endpoints.
     *
     * @param response the HTTP response
     * @param tokenValue the refresh token value
     */
    private void setRefreshTokenCookie(final HttpServletResponse response, final String tokenValue) {
        final ResponseCookie cookie = refreshTokenService.createRefreshTokenCookie(tokenValue);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clears the refresh token cookie.
     *
     * @param response the HTTP response
     */
    private void clearRefreshTokenCookie(final HttpServletResponse response) {
        final ResponseCookie cookie = refreshTokenService.createClearRefreshTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        // Clear legacy path variant (/api/auth/refresh) to cover pre-fix deployments
        final ResponseCookie legacyCookie = refreshTokenService.createLegacyClearRefreshTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, legacyCookie.toString());
    }

    /**
     * Sets the fingerprint cookie using TokenFingerprintService (ADR-0052 Phase C).
     * Cookie max-age is aligned with JWT expiration to prevent silent auth failures.
     *
     * @param response the HTTP response
     * @param fingerprint the raw fingerprint value
     */
    private void setFingerprintCookie(final HttpServletResponse response, final String fingerprint) {
        final Duration maxAge = Duration.ofMillis(jwtService.getExpirationTime());
        final ResponseCookie cookie = fingerprintService.createFingerprintCookie(fingerprint, secureCookie, maxAge);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        // Clear the opposite variant to avoid stale cookies when switching HTTP↔HTTPS
        final ResponseCookie oppositeClearCookie = fingerprintService.createClearCookie(!secureCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, oppositeClearCookie.toString());
    }

    /**
     * Clears both fingerprint cookie variants (HTTPS and HTTP dev names).
     * ADR-0052 specifies __Secure-Fgp for HTTPS and Fgp for HTTP dev.
     *
     * @param response the HTTP response
     */
    private void clearFingerprintCookies(final HttpServletResponse response) {
        // Clear the secure cookie variant
        final ResponseCookie secureClearCookie = fingerprintService.createClearCookie(true);
        response.addHeader(HttpHeaders.SET_COOKIE, secureClearCookie.toString());
        // Clear the dev cookie variant
        final ResponseCookie devClearCookie = fingerprintService.createClearCookie(false);
        response.addHeader(HttpHeaders.SET_COOKIE, devClearCookie.toString());
    }
}
