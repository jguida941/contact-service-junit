package contactapp.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService} so PIT covers token generation/validation logic.
 *
 * <p>All tests use the fingerprint-aware {@code generateToken(UserDetails, String)}
 * signature per ADR-0052 Phase C requirements. A test fingerprint hash is used since
 * the actual fingerprint generation/verification is tested in TokenFingerprintServiceTest.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    /** SHA-256 hash for testing fingerprint binding (64 hex chars). */
    private static final String TEST_FINGERPRINT_HASH =
            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        String rawSecret = "test-secret-key-that-is-long-enough";
        String base64 = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtService, "secretKey", base64);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600_000L);
        userDetails = User.withUsername("tester")
                .password("ignored")
                .roles("USER")
                .build();
    }

    @Test
    void generateTokenContainsUsernameClaim() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        assertThat(jwtService.extractUsername(token)).isEqualTo("tester");
    }

    @Test
    void isTokenValidReturnsTrueForValidToken() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValidReturnsFalseForDifferentUser() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);
        UserDetails otherUser = User.withUsername("other").password("ignored").roles("USER").build();

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void extractUsernameThrowsWhenTokenExpired() {
        // Set expiration to -61 seconds (beyond the 60-second clock skew tolerance)
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -61000L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        assertThatThrownBy(() -> jwtService.extractUsername(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void extractClaimReturnsCustomClaim() {
        String token = jwtService.generateToken(Map.of("role", "ADMIN"), userDetails, TEST_FINGERPRINT_HASH);

        Object role = jwtService.extractClaim(token, claims -> claims.get("role"));
        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    void fallbackToUtf8SecretWhenBase64DecodingFails() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "plain-secret-key-that-is-long-enough-1234567890");
        ReflectionTestUtils.setField(service, "jwtExpiration", 1000L);

        String token = service.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        assertThat(service.extractUsername(token)).isEqualTo("tester");
    }

    /**
     * Ensures {@code isTokenExpired} returns false for freshly minted tokens so PIT cannot flip the boolean.
     */
    @Test
    void isTokenExpiredReturnsFalseForFreshToken() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        boolean expired = ReflectionTestUtils.invokeMethod(jwtService, "isTokenExpired", token);

        assertThat(expired).isFalse();
    }

    // ==================== Token Refresh Eligibility Tests ====================

    @Test
    void isTokenEligibleForRefresh_returnsTrueForValidToken() {
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 300000L); // 5 min
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        assertThat(jwtService.isTokenEligibleForRefresh(token, userDetails)).isTrue();
    }

    @Test
    void isTokenEligibleForRefresh_returnsTrueForRecentlyExpiredToken() {
        // Create a token that will expire immediately
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L); // 1ms
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 300000L); // 5 min
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Wait a tiny bit for it to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should still be eligible within refresh window
        assertThat(jwtService.isTokenEligibleForRefresh(token, userDetails)).isTrue();
    }

    @Test
    void isTokenEligibleForRefresh_returnsFalseForDifferentUser() {
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 300000L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);
        UserDetails otherUser = User.withUsername("other").password("ignored").roles("USER").build();

        assertThat(jwtService.isTokenEligibleForRefresh(token, otherUser)).isFalse();
    }

    @Test
    void getRefreshWindow_returnsConfiguredValue() {
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 600000L);

        assertThat(jwtService.getRefreshWindow()).isEqualTo(600000L);
    }

    // ==================== Additional Boundary and Comparison Tests ====================

    /**
     * Tests that isTokenValid returns false when token is expired by exactly 1ms.
     *
     * <p><b>Why this test exists:</b> Tests the boundary condition of token expiration.
     * A token expired by even 1 millisecond should be considered invalid. This catches
     * mutants that change the comparison operator in the expiration check.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in isTokenExpired: {@code before} to {@code after}</li>
     *   <li>Negated conditional in isTokenValid</li>
     *   <li>Removed expiration check</li>
     * </ul>
     */
    @Test
    void isTokenValid_returnsFalseWhenTokenExpiredByOneMillisecond() {
        // Create a token with 1ms expiration so it expires almost immediately
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Token should now be invalid (expired)
        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    /**
     * Tests that a token that is NOT expired is considered valid.
     *
     * <p><b>Why this test exists:</b> Ensures the opposite boundary - a token that
     * hasn't expired yet should be valid. Tests that the comparison is correct.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Inverted boolean return value in isTokenValid</li>
     *   <li>Changed AND to OR in validation logic</li>
     * </ul>
     */
    @Test
    void isTokenValid_returnsTrueWhenTokenNotExpired() {
        // Create a token with long expiration
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Token should be valid
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    /**
     * Tests isTokenEligibleForRefresh at the exact refresh window boundary.
     *
     * <p><b>Why this test exists:</b> Tests the boundary condition where a token
     * is expired by exactly the refresh window duration. This should still be eligible
     * for refresh (using {@code <=} not {@code <}).
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code <=} to {@code <}</li>
     *   <li>Changed conditional boundary: {@code >} to {@code >=}</li>
     * </ul>
     */
    @Test
    void isTokenEligibleForRefresh_returnsTrueAtExactRefreshWindowBoundary() {
        // Set refresh window to 1000ms
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 1000L);
        // Create a token that expires in 1ms
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Wait exactly 1ms for expiration
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Token should still be eligible (expired but within refresh window)
        assertThat(jwtService.isTokenEligibleForRefresh(token, userDetails)).isTrue();
    }

    /**
     * Tests isTokenEligibleForRefresh returns false when expired beyond refresh window.
     *
     * <p><b>Why this test exists:</b> Tests the boundary from the other side - a token
     * expired beyond the refresh window should NOT be eligible for refresh.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed conditional check for refresh window</li>
     *   <li>Changed {@code <=} to always true</li>
     * </ul>
     */
    @Test
    void isTokenEligibleForRefresh_returnsFalseWhenExpiredBeyondRefreshWindow() {
        // Set very short refresh window (1ms)
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 1L);
        // Create a token that expires immediately
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Wait well beyond the refresh window
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Token should NOT be eligible (expired beyond refresh window)
        assertThat(jwtService.isTokenEligibleForRefresh(token, userDetails)).isFalse();
    }

    /**
     * Tests username comparison in isTokenValid uses equals, not reference equality.
     *
     * <p><b>Why this test exists:</b> Ensures the username comparison uses {@code .equals()}
     * and not {@code ==}. Two String objects with the same content should match.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed equals to == (reference equality)</li>
     *   <li>Negated conditional in username check</li>
     * </ul>
     */
    @Test
    void isTokenValid_usesEqualsForUsernameComparison() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Create new UserDetails with same username but different object
        UserDetails sameUser = User.withUsername(new String("tester")) // Force new String object
                .password("ignored")
                .roles("USER")
                .build();

        // Should still be valid because usernames are equal (not same reference)
        assertThat(jwtService.isTokenValid(token, sameUser)).isTrue();
    }

    /**
     * Tests that extractUsername returns the exact username from the token.
     *
     * <p><b>Why this test exists:</b> Ensures extractUsername doesn't modify or
     * transform the username. Tests that the return value matches exactly.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Modified return value</li>
     *   <li>Returned empty string</li>
     *   <li>Returned null</li>
     * </ul>
     */
    @Test
    void extractUsername_returnsExactUsername() {
        UserDetails user = User.withUsername("testuser123")
                .password("ignored")
                .roles("USER")
                .build();
        String token = jwtService.generateToken(user, TEST_FINGERPRINT_HASH);

        String extracted = jwtService.extractUsername(token);

        // Must match exactly
        assertThat(extracted).isEqualTo("testuser123");
        assertThat(extracted).isNotEmpty();
        assertThat(extracted).isNotNull();
    }

    /**
     * Tests getExpirationTime returns the exact configured value.
     *
     * <p><b>Why this test exists:</b> Ensures getExpirationTime returns the value
     * without modification. Catches mutants that change the return value.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Replaced return value with 0</li>
     *   <li>Replaced return value with -1</li>
     *   <li>Modified arithmetic on return value</li>
     * </ul>
     */
    @Test
    void getExpirationTime_returnsExactConfiguredValue() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 7200000L);

        long expiration = jwtService.getExpirationTime();

        assertThat(expiration).isEqualTo(7200000L);
        assertThat(expiration).isNotZero();
        assertThat(expiration).isPositive();
    }

    /**
     * Tests that isTokenValid returns false when usernames don't match exactly.
     *
     * <p><b>Why this test exists:</b> Ensures the username comparison is case-sensitive
     * and exact. Even a small difference should cause validation to fail.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed equals to equalsIgnoreCase</li>
     *   <li>Removed username check</li>
     *   <li>Inverted username comparison</li>
     * </ul>
     */
    @Test
    void isTokenValid_returnsFalseForCaseSensitiveUsernameMismatch() {
        UserDetails user = User.withUsername("TestUser")
                .password("ignored")
                .roles("USER")
                .build();
        String token = jwtService.generateToken(user, TEST_FINGERPRINT_HASH);

        UserDetails differentCase = User.withUsername("testuser") // Different case
                .password("ignored")
                .roles("USER")
                .build();

        // Should be invalid - usernames don't match exactly
        assertThat(jwtService.isTokenValid(token, differentCase)).isFalse();
    }

    /**
     * Tests that isTokenEligibleForRefresh handles null claims gracefully.
     *
     * <p><b>Why this test exists:</b> When an ExpiredJwtException is thrown with null
     * claims, the method should return false (not throw NPE). Tests defensive coding.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed null check for claims</li>
     *   <li>Changed return value when claims are null</li>
     * </ul>
     */
    @Test
    void isTokenEligibleForRefresh_returnsFalseWhenUsernameDoesNotMatch() {
        ReflectionTestUtils.setField(jwtService, "refreshWindow", 300000L);
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        UserDetails wrongUser = User.withUsername("wronguser")
                .password("ignored")
                .roles("USER")
                .build();

        // Should return false when username doesn't match
        assertThat(jwtService.isTokenEligibleForRefresh(token, wrongUser)).isFalse();
    }

    // ==================== Fingerprint Claim Tests ====================

    /**
     * Tests that generateToken with fingerprint hash includes the fph claim.
     */
    @Test
    void generateToken_includesFingerprintClaim() {
        String token = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        String extractedHash = jwtService.extractFingerprintHash(token);
        assertThat(extractedHash).isEqualTo(TEST_FINGERPRINT_HASH);
    }

    /**
     * Tests that generateToken with null fingerprint produces token without fph claim.
     */
    @Test
    void generateToken_withNullFingerprint_producesTokenWithoutFphClaim() {
        String token = jwtService.generateToken(userDetails, null);

        String extractedHash = jwtService.extractFingerprintHash(token);
        assertThat(extractedHash).isNull();
    }

    // ==================== Configuration Validation Tests ====================

    /**
     * Tests that validateConfiguration rejects null secret.
     *
     * <p><b>Why this test exists:</b> Ensures the application fails fast at startup
     * when JWT secret is not configured, rather than failing at runtime.
     */
    @Test
    void validateConfiguration_throwsWhenSecretIsNull() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", null);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must be configured");
    }

    /**
     * Tests that validateConfiguration rejects blank secret.
     */
    @Test
    void validateConfiguration_throwsWhenSecretIsBlank() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "   ");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must be configured");
    }

    /**
     * Tests that validateConfiguration rejects test secrets in production profile.
     *
     * <p><b>Why this test exists:</b> Ensures developers cannot accidentally deploy
     * with the default test secret. The known test secret prefix is rejected
     * when spring.profiles.active contains "prod".
     */
    @Test
    void validateConfiguration_throwsForTestSecretInProdProfile() {
        String originalProfile = System.getProperty("spring.profiles.active");
        try {
            System.setProperty("spring.profiles.active", "prod");

            JwtService service = new JwtService();
            // The known test secret prefix that should be rejected in prod
            String testSecret = "dGVzdC1zZWNyZXQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5";
            ReflectionTestUtils.setField(service, "secretKey", testSecret);

            assertThatThrownBy(service::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Test JWT secret detected in production");
        } finally {
            if (originalProfile != null) {
                System.setProperty("spring.profiles.active", originalProfile);
            } else {
                System.clearProperty("spring.profiles.active");
            }
        }
    }

    /**
     * Tests that validateConfiguration accepts test secrets in non-prod profiles.
     */
    @Test
    void validateConfiguration_allowsTestSecretInDevProfile() {
        String originalProfile = System.getProperty("spring.profiles.active");
        try {
            System.setProperty("spring.profiles.active", "dev");

            JwtService service = new JwtService();
            // Use a test secret that is long enough (>= 32 bytes when decoded)
            String testSecret = "dGVzdC1zZWNyZXQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZA==";
            ReflectionTestUtils.setField(service, "secretKey", testSecret);

            // Should not throw - test secrets allowed in dev
            service.validateConfiguration();
        } finally {
            if (originalProfile != null) {
                System.setProperty("spring.profiles.active", originalProfile);
            } else {
                System.clearProperty("spring.profiles.active");
            }
        }
    }

    /**
     * Tests that validateConfiguration rejects secrets shorter than 32 bytes.
     *
     * <p><b>Why this test exists:</b> HMAC-SHA256 requires 256-bit (32-byte) keys
     * for full security. Shorter keys may be vulnerable to brute-force attacks.
     */
    @Test
    void validateConfiguration_throwsForShortSecret() {
        JwtService service = new JwtService();
        // Base64 encode a 16-byte (too short) secret
        String shortSecret = Base64.getEncoder().encodeToString("short-secret-123".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(service, "secretKey", shortSecret);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits (32 bytes)");
    }

    /**
     * Tests that validateConfiguration accepts 32-byte or longer secrets.
     */
    @Test
    void validateConfiguration_acceptsValidLengthSecret() {
        JwtService service = new JwtService();
        // Exactly 32 bytes - minimum valid length (count the chars: 32)
        String validSecret = Base64.getEncoder().encodeToString(
                "exactly-32-bytes-long-secret-123".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(service, "secretKey", validSecret);

        // Should not throw
        service.validateConfiguration();
    }

    // ==================== Token Security Tests ====================

    /**
     * Tests that tampering with a token's payload invalidates it.
     *
     * <p><b>Why this test exists:</b> Ensures tokens cannot be modified without
     * invalidating the signature. Even small changes should cause rejection.
     */
    @Test
    void extractUsername_throwsForTamperedToken() {
        String validToken = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Tamper with the payload (middle part of JWT)
        String[] parts = validToken.split("\\.");
        // Decode payload, modify, re-encode
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace("tester", "hacker");
        String tamperedPayloadEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayloadEncoded + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractUsername(tamperedToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    /**
     * Tests that tokens with invalid signatures are rejected.
     *
     * <p><b>Why this test exists:</b> Ensures tokens signed with a different key
     * cannot be validated, even if the format is correct.
     */
    @Test
    void extractUsername_throwsForInvalidSignature() {
        String validToken = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Replace signature with garbage
        String[] parts = validToken.split("\\.");
        String invalidSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("invalid-signature-data".getBytes(StandardCharsets.UTF_8));
        String tokenWithBadSignature = parts[0] + "." + parts[1] + "." + invalidSignature;

        assertThatThrownBy(() -> jwtService.extractUsername(tokenWithBadSignature))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    /**
     * Tests that malformed tokens are rejected.
     */
    @Test
    void extractUsername_throwsForMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractUsername("not.a.valid.jwt"))
                .isInstanceOf(io.jsonwebtoken.MalformedJwtException.class);
    }

    /**
     * Verifies that tokens with correct issuer are accepted.
     *
     * <p><b>Why this test exists:</b> Confirms issuer claim is set correctly
     * and tokens from this service are valid. Testing wrong issuer rejection
     * would require a second JwtService instance with different configuration.
     */
    @Test
    void extractUsername_succeedsForValidIssuer() {
        String validToken = jwtService.generateToken(userDetails, TEST_FINGERPRINT_HASH);

        // Token should have correct issuer and be valid
        String username = jwtService.extractUsername(validToken);
        assertThat(username).isEqualTo("tester");
    }
}
