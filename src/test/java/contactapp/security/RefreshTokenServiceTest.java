package contactapp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenService}.
 *
 * <p>Tests the refresh token lifecycle: create, validate, revoke, rotate, cleanup.
 * Per ADR-0052 Phase B, these tests verify:
 * <ul>
 *   <li>Single-session enforcement (existing tokens revoked on create)</li>
 *   <li>Token validation (non-revoked AND non-expired)</li>
 *   <li>Token rotation on refresh</li>
 *   <li>Mass revocation for logout/security events</li>
 *   <li>Cookie security attributes (HttpOnly, scoped path)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        // Set expiration via reflection (normally injected by Spring)
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", REFRESH_EXPIRATION_MS);
        ReflectionTestUtils.setField(refreshTokenService, "secureCookie", true);

        // Use a valid BCrypt hash (User constructor validates password format)
        // Format: $2a$10$ + 53 characters = 60 total characters
        final String validBcryptHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoOBiZFn0eTD1yQbFq6Z9erjRzCQXDpG7W";
        testUser = new User("testuser", "test@example.com", validBcryptHash, Role.USER);
        // Set user ID via reflection
        ReflectionTestUtils.setField(testUser, "id", USER_ID);
    }

    // ==================== Create Token Tests ====================

    @Test
    void createRefreshToken_revokesExistingTokens() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(2);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        refreshTokenService.createRefreshToken(testUser);

        // Assert - existing tokens should be revoked first
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    void createRefreshToken_generatesSecureToken() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(0);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        final RefreshToken token = refreshTokenService.createRefreshToken(testUser);

        // Assert
        assertThat(token.getToken()).isNotBlank();
        // Base64 URL encoding of 32 bytes = 43 characters (256 bits of entropy)
        assertThat(token.getToken()).hasSize(43);
        assertThat(token.getUser()).isEqualTo(testUser);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiryDate()).isAfter(Instant.now());
    }

    @Test
    void createRefreshToken_setsCorrectExpiry() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(0);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        final Instant beforeCreate = Instant.now();

        // Act
        final RefreshToken token = refreshTokenService.createRefreshToken(testUser);

        // Assert - expiry should be approximately 7 days from now
        final Instant afterCreate = Instant.now();
        assertThat(token.getExpiryDate())
                .isAfter(beforeCreate.plusMillis(REFRESH_EXPIRATION_MS - 1000))
                .isBefore(afterCreate.plusMillis(REFRESH_EXPIRATION_MS + 1000));
    }

    @Test
    void createRefreshToken_persistsToken() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(0);
        final ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(tokenCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        // Act
        refreshTokenService.createRefreshToken(testUser);

        // Assert
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        final RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(testUser);
    }

    @Test
    void createLegacyClearRefreshTokenCookie_clearsOldPath() {
        // Regression guard: legacy cookies used /api/auth/refresh so logout must clear that scope too.
        final ResponseCookie cookie = refreshTokenService.createLegacyClearRefreshTokenCookie();

        assertThat(cookie.getName()).isEqualTo(RefreshTokenService.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).contains("Lax");
    }

    // ==================== Find Valid Token Tests ====================

    @Test
    void findValidToken_returnsToken_whenValidAndNotExpired() {
        // Arrange
        final RefreshToken validToken = new RefreshToken(testUser, "valid-token", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
                .thenReturn(Optional.of(validToken));

        // Act
        final Optional<RefreshToken> result = refreshTokenService.findValidToken("valid-token");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo("valid-token");
    }

    @Test
    void findValidToken_returnsEmpty_whenTokenNotFound() {
        // Arrange
        when(refreshTokenRepository.findByTokenAndRevokedFalse("nonexistent"))
                .thenReturn(Optional.empty());

        // Act
        final Optional<RefreshToken> result = refreshTokenService.findValidToken("nonexistent");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findValidToken_returnsEmpty_whenTokenExpired() {
        // Arrange - token that is not revoked but is expired
        // Use createForTesting() to bypass constructor validation (simulates JPA loading an expired token)
        final RefreshToken expiredToken = RefreshToken.createForTesting(
                testUser, "expired-token", Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByTokenAndRevokedFalse("expired-token"))
                .thenReturn(Optional.of(expiredToken));

        // Act
        final Optional<RefreshToken> result = refreshTokenService.findValidToken("expired-token");

        // Assert - should filter out expired tokens
        assertThat(result).isEmpty();
    }

    // ==================== Revoke Token Tests ====================

    @Test
    void revokeToken_revokesExistingToken() {
        // Arrange
        final RefreshToken token = new RefreshToken(testUser, "token-to-revoke", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken("token-to-revoke")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        final boolean result = refreshTokenService.revokeToken("token-to-revoke");

        // Assert
        assertThat(result).isTrue();
        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeToken_returnsFalse_whenTokenNotFound() {
        // Arrange
        when(refreshTokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        // Act
        final boolean result = refreshTokenService.revokeToken("nonexistent");

        // Assert
        assertThat(result).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeToken_returnsFalse_whenAlreadyRevoked() {
        // Arrange
        final RefreshToken alreadyRevoked = new RefreshToken(testUser, "already-revoked", Instant.now().plusSeconds(3600));
        alreadyRevoked.revoke(); // Already revoked
        when(refreshTokenRepository.findByToken("already-revoked")).thenReturn(Optional.of(alreadyRevoked));

        // Act
        final boolean result = refreshTokenService.revokeToken("already-revoked");

        // Assert
        assertThat(result).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    // ==================== Revoke All User Tokens Tests ====================

    @Test
    void revokeAllUserTokens_revokesAllTokensForUser() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(3);

        // Act
        final int revoked = refreshTokenService.revokeAllUserTokens(testUser);

        // Assert
        assertThat(revoked).isEqualTo(3);
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    void revokeAllUserTokens_returnsZero_whenNoTokensExist() {
        // Arrange
        when(refreshTokenRepository.revokeAllByUserId(USER_ID)).thenReturn(0);

        // Act
        final int revoked = refreshTokenService.revokeAllUserTokens(testUser);

        // Assert
        assertThat(revoked).isZero();
    }

    // ==================== Atomic Validate and Revoke Tests ====================
    // Added to kill mutation: "removed call to RefreshToken::revoke" at validateAndRevokeAtomically:143

    /**
     * Tests that validateAndRevokeAtomically returns the token when valid.
     *
     * <p><b>Why this test exists:</b> Verifies the happy path where a valid token
     * is found, validated, and returned for further processing.
     */
    @Test
    void validateAndRevokeAtomically_returnsToken_whenValid() {
        // Arrange
        final RefreshToken validToken = new RefreshToken(testUser, "valid-token", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenForUpdate("valid-token"))
                .thenReturn(Optional.of(validToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        final Optional<RefreshToken> result = refreshTokenService.validateAndRevokeAtomically("valid-token");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo("valid-token");
    }

    /**
     * Tests that validateAndRevokeAtomically actually revokes the token.
     *
     * <p><b>Why this test exists:</b> This test kills the critical mutation where
     * the revoke() call is removed. Without revoking, tokens could be reused
     * allowing session hijacking attacks.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>VoidMethodCallMutator: removed call to RefreshToken::revoke at line 143</li>
     * </ul>
     */
    @Test
    void validateAndRevokeAtomically_revokesTheToken() {
        // Arrange
        final RefreshToken validToken = new RefreshToken(testUser, "to-revoke", Instant.now().plusSeconds(3600));
        assertThat(validToken.isRevoked()).isFalse(); // Precondition: not revoked yet

        when(refreshTokenRepository.findByTokenForUpdate("to-revoke"))
                .thenReturn(Optional.of(validToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        refreshTokenService.validateAndRevokeAtomically("to-revoke");

        // Assert - token MUST be revoked after the atomic operation
        assertThat(validToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(validToken);
    }

    /**
     * Tests that validateAndRevokeAtomically returns empty when token not found.
     *
     * <p><b>Why this test exists:</b> Ensures proper handling of missing tokens
     * without throwing exceptions.
     */
    @Test
    void validateAndRevokeAtomically_returnsEmpty_whenNotFound() {
        // Arrange
        when(refreshTokenRepository.findByTokenForUpdate("nonexistent"))
                .thenReturn(Optional.empty());

        // Act
        final Optional<RefreshToken> result = refreshTokenService.validateAndRevokeAtomically("nonexistent");

        // Assert
        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    /**
     * Tests that validateAndRevokeAtomically returns empty when token expired.
     *
     * <p><b>Why this test exists:</b> Expired tokens must be rejected even if
     * found in the database. Uses createForTesting() to simulate an expired token
     * loaded from persistence.
     */
    @Test
    void validateAndRevokeAtomically_returnsEmpty_whenExpired() {
        // Arrange - use createForTesting to bypass constructor validation
        final RefreshToken expiredToken = RefreshToken.createForTesting(
                testUser, "expired-token", Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByTokenForUpdate("expired-token"))
                .thenReturn(Optional.of(expiredToken));

        // Act
        final Optional<RefreshToken> result = refreshTokenService.validateAndRevokeAtomically("expired-token");

        // Assert
        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    // ==================== Cookie Creation Tests ====================

    @Test
    void createRefreshTokenCookie_setsSecurityAttributes() {
        // Act
        final ResponseCookie cookie = refreshTokenService.createRefreshTokenCookie("test-token-value");

        // Assert
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("test-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue(); // secureCookie = true in setUp
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    void createRefreshTokenCookie_setsCorrectMaxAge() {
        // Act
        final ResponseCookie cookie = refreshTokenService.createRefreshTokenCookie("test-token");

        // Assert - maxAge should be 7 days in seconds
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(REFRESH_EXPIRATION_MS / 1000);
    }

    @Test
    void createClearRefreshTokenCookie_setsZeroMaxAge() {
        // Act
        final ResponseCookie cookie = refreshTokenService.createClearRefreshTokenCookie();

        // Assert
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    /**
     * Tests that legacy clear cookie uses the old /api/auth/refresh path.
     *
     * <p><b>Why this test exists:</b> Older tokens were scoped to /api/auth/refresh
     * and need explicit clearing on that path. This test ensures the legacy path
     * is preserved for backwards compatibility during logout.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>NullReturnValsMutator: replaced return value with null at line 206</li>
     * </ul>
     */
    @Test
    void createLegacyClearRefreshTokenCookie_usesLegacyPath() {
        // Act
        final ResponseCookie cookie = refreshTokenService.createLegacyClearRefreshTokenCookie();

        // Assert
        assertThat(cookie).isNotNull();
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        // Key distinction: legacy path is /api/auth/refresh, not /api/auth
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
    }

    // ==================== Cleanup Tests ====================

    @Test
    void cleanupExpiredTokens_deletesExpiredTokens() {
        // Arrange
        when(refreshTokenRepository.deleteAllExpired(any(Instant.class))).thenReturn(5);

        // Act
        refreshTokenService.cleanupExpiredTokens();

        // Assert
        verify(refreshTokenRepository).deleteAllExpired(any(Instant.class));
    }

    @Test
    void getRefreshExpiration_returnsConfiguredValue() {
        // Act
        final long expiration = refreshTokenService.getRefreshExpiration();

        // Assert
        assertThat(expiration).isEqualTo(REFRESH_EXPIRATION_MS);
    }
}
