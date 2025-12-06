package contactapp.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mutation-killing tests for TokenFingerprintService.
 *
 * <p>Targets PITest mutations in fingerprint generation, hashing, verification,
 * and cookie extraction logic.
 */
class TokenFingerprintServiceTest {

    private TokenFingerprintService service;

    @BeforeEach
    void setUp() {
        service = new TokenFingerprintService();
    }

    // ==================== Fingerprint Generation Tests ====================

    /**
     * Kills mutation: removed call to SecureRandom.nextBytes
     * Verifies fingerprint contains random bytes (not all zeros).
     */
    @Test
    void generateFingerprint_producesRandomContent() {
        String fp1 = service.generateFingerprint();
        String fp2 = service.generateFingerprint();

        // Two fingerprints should be different (probabilistically)
        assertThat(fp1).isNotEqualTo(fp2);

        // Should be 100 hex chars (50 bytes * 2)
        assertThat(fp1).hasSize(100);
        assertThat(fp2).hasSize(100);

        // Should contain valid hex characters only
        assertThat(fp1).matches("[0-9a-f]+");
    }

    /**
     * Verifies fingerprint is not all zeros (would be if nextBytes not called).
     */
    @Test
    void generateFingerprint_isNotAllZeros() {
        String fp = service.generateFingerprint();

        // Should not be all zeros
        assertThat(fp).isNotEqualTo("0".repeat(100));
    }

    // ==================== Hash Tests ====================

    /**
     * Kills mutation: replaced return value with ""
     * Verifies hash is non-empty.
     */
    @Test
    void hashFingerprint_returnsNonEmptyHash() {
        String fingerprint = service.generateFingerprint();
        String hash = service.hashFingerprint(fingerprint);

        assertThat(hash).isNotEmpty();
        assertThat(hash).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
    }

    /**
     * Verifies hash is deterministic (same input = same output).
     */
    @Test
    void hashFingerprint_isDeterministic() {
        String fingerprint = "test-fingerprint-value-12345";
        String hash1 = service.hashFingerprint(fingerprint);
        String hash2 = service.hashFingerprint(fingerprint);

        assertThat(hash1).isEqualTo(hash2);
    }

    /**
     * Verifies hash is different for different inputs.
     */
    @Test
    void hashFingerprint_producesUniqueHashesForDifferentInputs() {
        String hash1 = service.hashFingerprint("fingerprint1");
        String hash2 = service.hashFingerprint("fingerprint2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashFingerprint_throwsForNull() {
        assertThatThrownBy(() -> service.hashFingerprint(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void hashFingerprint_throwsForEmpty() {
        assertThatThrownBy(() -> service.hashFingerprint(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    // ==================== Verification Tests ====================

    /**
     * Kills mutation: replaced boolean return with true
     * Tests that verification returns FALSE for mismatched fingerprint.
     */
    @Test
    void verifyFingerprint_returnsFalseForMismatch() {
        String fingerprint = service.generateFingerprint();
        String correctHash = service.hashFingerprint(fingerprint);
        String wrongHash = "0000000000000000000000000000000000000000000000000000000000000000";

        boolean result = service.verifyFingerprint(fingerprint, wrongHash);

        assertThat(result).isFalse();
    }

    /**
     * Tests verification returns true for matching fingerprint.
     */
    @Test
    void verifyFingerprint_returnsTrueForMatch() {
        String fingerprint = service.generateFingerprint();
        String hash = service.hashFingerprint(fingerprint);

        boolean result = service.verifyFingerprint(fingerprint, hash);

        assertThat(result).isTrue();
    }

    /**
     * Kills mutation on null check.
     */
    @Test
    void verifyFingerprint_returnsFalseForNullFingerprint() {
        boolean result = service.verifyFingerprint(null, "somehash");

        assertThat(result).isFalse();
    }

    /**
     * Kills mutation on empty check.
     */
    @Test
    void verifyFingerprint_returnsFalseForEmptyFingerprint() {
        boolean result = service.verifyFingerprint("", "somehash");

        assertThat(result).isFalse();
    }

    /**
     * Kills mutation on null expectedHash check.
     */
    @Test
    void verifyFingerprint_returnsFalseForNullExpectedHash() {
        boolean result = service.verifyFingerprint("somefp", null);

        assertThat(result).isFalse();
    }

    /**
     * Kills mutation on empty expectedHash check.
     */
    @Test
    void verifyFingerprint_returnsFalseForEmptyExpectedHash() {
        boolean result = service.verifyFingerprint("somefp", "");

        assertThat(result).isFalse();
    }

    // ==================== Cookie Name Tests ====================

    @Test
    void getCookieName_returnsSecurePrefixWhenSecure() {
        assertThat(service.getCookieName(true)).isEqualTo("__Secure-Fgp");
    }

    @Test
    void getCookieName_returnsDevNameWhenNotSecure() {
        assertThat(service.getCookieName(false)).isEqualTo("Fgp");
    }

    // ==================== Cookie Creation Tests ====================

    /**
     * Kills mutation: replaced return value with null
     */
    @Test
    void createFingerprintCookie_returnsNonNullCookie() {
        String fingerprint = service.generateFingerprint();

        ResponseCookie cookie = service.createFingerprintCookie(fingerprint, true);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(fingerprint);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void createFingerprintCookie_insecureMode() {
        String fingerprint = service.generateFingerprint();

        ResponseCookie cookie = service.createFingerprintCookie(fingerprint, false);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getName()).isEqualTo("Fgp");
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void createFingerprintCookie_withExplicitMaxAge() {
        String fingerprint = service.generateFingerprint();
        Duration maxAge = Duration.ofHours(1);

        ResponseCookie cookie = service.createFingerprintCookie(fingerprint, true, maxAge);

        assertThat(cookie.getMaxAge()).isEqualTo(maxAge);
    }

    @Test
    void createClearCookie_setsMaxAgeToZero() {
        ResponseCookie clearCookie = service.createClearCookie(true);

        assertThat(clearCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(clearCookie.getValue()).isEmpty();
    }

    // ==================== Cookie Extraction Tests ====================

    /**
     * Kills mutations in extractFingerprint lambda filters.
     */
    @Test
    void extractFingerprint_prefersSecureCookieOverDevCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie secureCookie = new Cookie("__Secure-Fgp", "secure-value");
        Cookie devCookie = new Cookie("Fgp", "dev-value");
        request.setCookies(secureCookie, devCookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).hasValue("secure-value");
    }

    @Test
    void extractFingerprint_fallsBackToDevCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie devCookie = new Cookie("Fgp", "dev-value");
        request.setCookies(devCookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).hasValue("dev-value");
    }

    /**
     * Kills mutation: replaced return value with Optional.empty
     */
    @Test
    void extractFingerprint_returnsValueWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie cookie = new Cookie("__Secure-Fgp", "my-fingerprint");
        request.setCookies(cookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("my-fingerprint");
    }

    @Test
    void extractFingerprint_returnsEmptyWhenNoCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No cookies set

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).isEmpty();
    }

    @Test
    void extractFingerprint_returnsEmptyWhenCookieValueEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie cookie = new Cookie("__Secure-Fgp", "");
        request.setCookies(cookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).isEmpty();
    }

    @Test
    void extractFingerprint_returnsEmptyWhenCookieValueNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie cookie = new Cookie("__Secure-Fgp", null);
        request.setCookies(cookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).isEmpty();
    }

    @Test
    void extractFingerprint_ignoresUnrelatedCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie unrelatedCookie = new Cookie("session_id", "abc123");
        request.setCookies(unrelatedCookie);

        Optional<String> result = service.extractFingerprint(request);

        assertThat(result).isEmpty();
    }

    // ==================== Fingerprint Pair Tests ====================

    @Test
    void generateFingerprintPair_producesRawAndHash() {
        TokenFingerprintService.FingerprintPair pair = service.generateFingerprintPair();

        assertThat(pair.raw()).isNotEmpty();
        assertThat(pair.hashed()).isNotEmpty();
        assertThat(pair.raw()).hasSize(100);
        assertThat(pair.hashed()).hasSize(64);

        // Hash should verify against raw
        assertThat(service.verifyFingerprint(pair.raw(), pair.hashed())).isTrue();
    }

    // ==================== Hex Encoding Tests ====================

    /**
     * Kills mutation: Replaced integer multiplication with division in bytesToHex
     * Verifies hex output length is correct (2 chars per byte).
     */
    @Test
    void generateFingerprint_hasCorrectLength() {
        // 50 bytes should produce 100 hex chars
        String fp = service.generateFingerprint();
        assertThat(fp).hasSize(100);
    }

    @Test
    void hashFingerprint_hasCorrectLength() {
        // SHA-256 is 32 bytes = 64 hex chars
        String hash = service.hashFingerprint("test");
        assertThat(hash).hasSize(64);
    }
}
