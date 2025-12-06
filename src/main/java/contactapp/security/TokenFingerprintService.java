package contactapp.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Service for generating and verifying token fingerprints.
 *
 * <p>Token fingerprinting binds a JWT to a specific client session by:
 * <ol>
 *   <li>Generating a random fingerprint value stored in an HttpOnly cookie</li>
 *   <li>Storing the SHA-256 hash of that fingerprint in the JWT</li>
 *   <li>Verifying the cookie fingerprint matches the JWT hash on each request</li>
 * </ol>
 *
 * <p>This provides defense-in-depth against token theft via XSS - an attacker
 * who steals the JWT cannot use it without also stealing the fingerprint cookie.
 *
 * <p>See ADR-0052 Phase C and OWASP JWT Cheat Sheet for details.
 */
@Service
public class TokenFingerprintService {

    /** Fingerprint length in bytes (50 bytes = 100 hex chars). */
    private static final int FINGERPRINT_BYTES = 50;

    /** SHA-256 hash length in hex chars (32 bytes = 64 hex chars). */
    private static final int HASH_HEX_LENGTH = 64;

    /**
     * Cookie name for HTTPS connections.
     * __Secure- prefix requires Secure=true and HTTPS, otherwise browsers reject it.
     */
    public static final String SECURE_FINGERPRINT_COOKIE = "__Secure-Fgp";

    /**
     * Cookie name for HTTP dev connections (no __Secure- prefix).
     * Used when running without HTTPS in local development.
     */
    public static final String DEV_FINGERPRINT_COOKIE = "Fgp";

    /** Default fingerprint cookie max age - fallback only, should use JWT expiration. */
    private static final Duration DEFAULT_COOKIE_MAX_AGE = Duration.ofMinutes(30);

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure random fingerprint.
     *
     * @return 100 character hex string (50 bytes of randomness)
     */
    public String generateFingerprint() {
        final byte[] randomBytes = new byte[FINGERPRINT_BYTES];
        secureRandom.nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    /**
     * Hashes a fingerprint using SHA-256.
     *
     * @param fingerprint the raw fingerprint value
     * @return 64 character hex string (SHA-256 hash)
     * @throws IllegalStateException if SHA-256 algorithm is not available
     */
    public String hashFingerprint(final String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("Fingerprint cannot be null or empty");
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hashBytes = digest.digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a fingerprint against a stored hash using constant-time comparison.
     *
     * <p>Uses {@link MessageDigest#isEqual} for constant-time comparison to prevent
     * timing attacks that could reveal information about the expected hash.
     *
     * @param fingerprint the raw fingerprint from the cookie
     * @param expectedHash the hash stored in the JWT
     * @return true if the fingerprint matches the hash
     */
    public boolean verifyFingerprint(final String fingerprint, final String expectedHash) {
        if (fingerprint == null || fingerprint.isEmpty()
                || expectedHash == null || expectedHash.isEmpty()) {
            return false;
        }
        final String actualHash = hashFingerprint(fingerprint);
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                actualHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /**
     * Gets the correct cookie name based on secure flag.
     * Uses __Secure-Fgp for HTTPS, plain Fgp for HTTP dev.
     *
     * @param secure true if running over HTTPS
     * @return the appropriate cookie name
     */
    public String getCookieName(final boolean secure) {
        return secure ? SECURE_FINGERPRINT_COOKIE : DEV_FINGERPRINT_COOKIE;
    }

    /**
     * Creates a hardened fingerprint cookie per OWASP guidelines.
     *
     * <p>Cookie attributes:
     * <ul>
     *   <li>HttpOnly - prevents JavaScript access</li>
     *   <li>Secure - only sent over HTTPS (when secure=true)</li>
     *   <li>SameSite=Lax - CSRF protection with usability balance</li>
     *   <li>Path=/ - available for all endpoints</li>
     *   <li>Max-Age - matches access token expiry</li>
     * </ul>
     *
     * @param fingerprint the raw fingerprint value
     * @param secure true if running over HTTPS
     * @return the configured ResponseCookie
     */
    public ResponseCookie createFingerprintCookie(final String fingerprint, final boolean secure) {
        return createFingerprintCookie(fingerprint, secure, DEFAULT_COOKIE_MAX_AGE);
    }

    /**
     * Creates a hardened fingerprint cookie with explicit max-age.
     *
     * <p>The max-age should match the JWT access token expiration to ensure
     * the fingerprint cookie and token expire together, preventing silent
     * auth failures when the cookie expires mid-session.
     *
     * @param fingerprint the raw fingerprint value
     * @param secure true if running over HTTPS
     * @param maxAge the cookie max age (should match JWT expiration)
     * @return the configured ResponseCookie
     */
    public ResponseCookie createFingerprintCookie(
            final String fingerprint,
            final boolean secure,
            final Duration maxAge
    ) {
        final String cookieName = getCookieName(secure);
        return ResponseCookie.from(cookieName, fingerprint)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /**
     * Creates a cookie that clears the fingerprint (for logout).
     *
     * @param secure true if running over HTTPS
     * @return the cookie configured to clear the fingerprint
     */
    public ResponseCookie createClearCookie(final boolean secure) {
        final String cookieName = getCookieName(secure);
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    /**
     * Extracts fingerprint from request cookie.
     * Checks both __Secure-Fgp (HTTPS) and Fgp (HTTP dev) cookie names.
     *
     * <p>When both cookies are present (e.g., switching from HTTP to HTTPS),
     * the __Secure-Fgp cookie takes priority to prevent stale cookie issues.
     *
     * @param request the HTTP request
     * @return Optional containing the fingerprint if found
     */
    public Optional<String> extractFingerprint(final HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        // Prefer __Secure-Fgp (HTTPS) over Fgp (HTTP) to handle HTTP→HTTPS transitions
        final Optional<String> secureCookie = Arrays.stream(cookies)
                .filter(c -> SECURE_FINGERPRINT_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .findFirst();

        if (secureCookie.isPresent()) {
            return secureCookie;
        }

        // Fall back to dev cookie for HTTP-only development
        return Arrays.stream(cookies)
                .filter(c -> DEV_FINGERPRINT_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .findFirst();
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    private String bytesToHex(final byte[] bytes) {
        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * A pair of raw fingerprint and its SHA-256 hash.
     * The raw value goes in the cookie, the hash goes in the JWT.
     *
     * @param raw the raw fingerprint (100 hex chars)
     * @param hashed the SHA-256 hash of the raw fingerprint (64 hex chars)
     */
    public record FingerprintPair(String raw, String hashed) { }

    /**
     * Generates a fingerprint pair (raw + hash) for use in login/refresh.
     *
     * @return a FingerprintPair with raw value for cookie and hash for JWT
     */
    public FingerprintPair generateFingerprintPair() {
        final String raw = generateFingerprint();
        final String hashed = hashFingerprint(raw);
        return new FingerprintPair(raw, hashed);
    }
}
