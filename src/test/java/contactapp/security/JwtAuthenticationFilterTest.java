package contactapp.security;

import contactapp.api.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private TokenFingerprintService fingerprintService;
    private JwtAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsService.class);
        fingerprintService = mock(TokenFingerprintService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, fingerprintService, false);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsFilterWhenAuthorizationHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void proceedsWhenTokenInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("bad token")).when(jwtService).extractUsername("invalid");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void setsAuthenticationWhenTokenValid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails userDetails = User.withUsername("tester").password("ignored").roles("USER").build();

        when(jwtService.extractUsername("validtoken")).thenReturn("tester");
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtService.isTokenValid("validtoken", userDetails)).thenReturn(true);
        // No fingerprint claim - backwards compatible (returns null)
        when(jwtService.extractFingerprintHash("validtoken")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("tester");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isNotNull();
    }

    @Test
    void extractJwtFromCookieReturnsTokenWhenPresent() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(AuthController.AUTH_COOKIE_NAME, "cookie-token");
        when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{cookie});

        final Optional<String> token = ReflectionTestUtils.invokeMethod(filter, "extractJwtFromCookie", request);

        assertThat(token).contains("cookie-token");
    }

    @Test
    void extractJwtFromCookieIgnoresBlankValues() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(AuthController.AUTH_COOKIE_NAME, "");
        when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{cookie});

        final Optional<String> token = ReflectionTestUtils.invokeMethod(filter, "extractJwtFromCookie", request);

        assertThat(token).isEmpty();
    }

    // ==================== Fingerprint Validation Tests (ADR-0052 Phase C) ====================

    @Test
    void setsAuthenticationWhenFingerprintMatches() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails userDetails = User.withUsername("tester").password("ignored").roles("USER").build();

        when(jwtService.extractUsername("validtoken")).thenReturn("tester");
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtService.isTokenValid("validtoken", userDetails)).thenReturn(true);
        // Token has fingerprint claim
        when(jwtService.extractFingerprintHash("validtoken")).thenReturn("expectedHash");
        // Fingerprint service returns matching cookie value
        when(fingerprintService.extractFingerprint(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("rawFingerprint"));
        when(fingerprintService.verifyFingerprint("rawFingerprint", "expectedHash")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("tester");
    }

    @Test
    void rejectsAuthenticationWhenFingerprintMismatch() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails userDetails = User.withUsername("tester").password("ignored").roles("USER").build();

        when(jwtService.extractUsername("validtoken")).thenReturn("tester");
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtService.isTokenValid("validtoken", userDetails)).thenReturn(true);
        // Token has fingerprint claim
        when(jwtService.extractFingerprintHash("validtoken")).thenReturn("expectedHash");
        // Fingerprint service returns cookie but hash doesn't match
        when(fingerprintService.extractFingerprint(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("wrongFingerprint"));
        when(fingerprintService.verifyFingerprint("wrongFingerprint", "expectedHash")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Authentication should NOT be set due to fingerprint mismatch
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsAuthenticationWhenFingerprintClaimEmptyString() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails userDetails = User.withUsername("tester").password("ignored").roles("USER").build();

        when(jwtService.extractUsername("validtoken")).thenReturn("tester");
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtService.isTokenValid("validtoken", userDetails)).thenReturn(true);
        // Fingerprint claim exists but is empty - should be treated as invalid
        when(jwtService.extractFingerprintHash("validtoken")).thenReturn("");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsAuthenticationWhenFingerprintCookieMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails userDetails = User.withUsername("tester").password("ignored").roles("USER").build();

        when(jwtService.extractUsername("validtoken")).thenReturn("tester");
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtService.isTokenValid("validtoken", userDetails)).thenReturn(true);
        // Token has fingerprint claim but no cookie present
        when(jwtService.extractFingerprintHash("validtoken")).thenReturn("expectedHash");
        when(fingerprintService.extractFingerprint(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Authentication should NOT be set - fingerprint required but cookie missing
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
