package contactapp.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing refresh tokens (ADR-0052 Phase B).
 *
 * <p>Handles the complete refresh token lifecycle:
 * <ul>
 *   <li>Token creation with automatic revocation of existing tokens</li>
 *   <li>Token validation (not revoked AND not expired)</li>
 *   <li>Token rotation on refresh (revoke old, create new)</li>
 *   <li>Mass revocation on logout or security events</li>
 *   <li>Scheduled cleanup of expired tokens</li>
 * </ul>
 *
 * <p>Security model: Single active session per user. Creating a new token
 * automatically revokes all existing tokens for that user.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    /** Cookie name for refresh token. */
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /** Cookie path - scoped to auth endpoints (refresh + logout). */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    /** Legacy cookie path used before logout revocation bugfix. */
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/api/auth/refresh";

    /** Token length in bytes (32 bytes = 256 bits of entropy). */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Value("${app.auth.cookie.secure:true}")
    private boolean secureCookie;

    public RefreshTokenService(final RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Creates a new refresh token for a user.
     * Revokes all existing tokens for single-session enforcement.
     *
     * @param user the user to create a token for
     * @return the new refresh token entity
     */
    @Transactional
    public RefreshToken createRefreshToken(final User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        // Revoke existing tokens (single active session model)
        final int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
        if (revoked > 0) {
            LOG.debug("Revoked {} existing refresh tokens for user ID {}", revoked, user.getId());
        }

        // Generate cryptographically secure token
        final String tokenValue = generateTokenValue();
        final Instant expiryDate = Instant.now().plusMillis(refreshExpiration);

        final RefreshToken refreshToken = new RefreshToken(user, tokenValue, expiryDate);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Finds a valid (non-revoked, non-expired) refresh token.
     *
     * @param tokenValue the token string from the cookie
     * @return the refresh token if valid, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValidToken(final String tokenValue) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenAndRevokedFalse(tokenValue)
                .filter(RefreshToken::isValid);
    }

    /**
     * Revokes a specific refresh token.
     *
     * @param tokenValue the token string to revoke
     * @return true if a token was revoked, false if not found
     */
    @Transactional
    public boolean revokeToken(final String tokenValue) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            return false;
        }
        final Optional<RefreshToken> token = refreshTokenRepository.findByToken(tokenValue);
        if (token.isPresent() && !token.get().isRevoked()) {
            token.get().revoke();
            refreshTokenRepository.save(token.get());
            LOG.debug("Revoked refresh token for user ID {}", token.get().getUser().getId());
            return true;
        }
        return false;
    }

    /**
     * Atomically validates and revokes a refresh token.
     *
     * <p>Uses pessimistic locking to prevent TOCTOU race conditions during
     * token rotation. If two concurrent requests try to refresh with the same
     * token, only the first will succeed; the second will find the token
     * already revoked.
     *
     * @param tokenValue the token string from the cookie
     * @return the refresh token if valid and successfully revoked, empty otherwise
     */
    @Transactional
    public Optional<RefreshToken> validateAndRevokeAtomically(final String tokenValue) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            return Optional.empty();
        }
        // Lock the row for update - concurrent requests will block here
        final Optional<RefreshToken> token = refreshTokenRepository.findByTokenForUpdate(tokenValue);

        if (token.isEmpty()) {
            LOG.debug("Refresh token not found or already revoked");
            return Optional.empty();
        }

        final RefreshToken refreshToken = token.get();

        // Check expiry (lock acquired, so no race here)
        if (!refreshToken.isValid()) {
            LOG.debug("Refresh token expired");
            return Optional.empty();
        }

        // Revoke the token atomically
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        LOG.debug("Atomically validated and revoked refresh token for user ID {}",
                refreshToken.getUser().getId());

        return Optional.of(refreshToken);
    }

    /**
     * Revokes all refresh tokens for a user.
     * Used on logout, password change, or security events.
     *
     * @param user the user whose tokens should be revoked
     * @return the number of tokens revoked
     */
    @Transactional
    public int revokeAllUserTokens(final User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        final int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
        if (revoked > 0) {
            LOG.info("Revoked all {} refresh tokens for user ID {}", revoked, user.getId());
        }
        return revoked;
    }

    /**
     * Creates a refresh token cookie.
     * Path is scoped to /api/auth for security (covers refresh and logout).
     *
     * @param tokenValue the token string
     * @return the configured ResponseCookie
     */
    public ResponseCookie createRefreshTokenCookie(final String tokenValue) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            throw new IllegalArgumentException("Token value cannot be null or empty");
        }
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenValue)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshExpiration))
                .build();
    }

    /**
     * Creates a cookie that clears the refresh token.
     *
     * @return the cookie configured to clear the refresh token
     */
    public ResponseCookie createClearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /**
     * Clears the refresh token cookie using the legacy /api/auth/refresh path.
     * Older tokens were set on this path and need to be explicitly cleared.
     *
     * @return the legacy clear-cookie variant
     */
    public ResponseCookie createLegacyClearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(LEGACY_REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /**
     * Returns the configured refresh token expiration.
     *
     * @return expiration time in milliseconds
     */
    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    /**
     * Scheduled job to clean up expired refresh tokens.
     * Runs every hour at minute 0.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        final Instant cutoff = Instant.now();
        final int deleted = refreshTokenRepository.deleteAllExpired(cutoff);
        if (deleted > 0) {
            LOG.info("Cleanup job deleted {} expired refresh tokens", deleted);
        }
    }

    /**
     * Generates a cryptographically secure random token value.
     *
     * @return Base64-encoded token string (URL-safe)
     */
    private String generateTokenValue() {
        final byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
