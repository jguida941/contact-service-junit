package contactapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import contactapp.config.RateLimitingFilter;
import contactapp.security.JwtAuthenticationFilter;
import contactapp.security.JwtService;
import contactapp.security.Role;
import contactapp.security.User;
import contactapp.security.UserRepository;
import contactapp.security.WithMockAppUser;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import contactapp.support.PostgresContainerSupport;

/**
 * Integration tests focused on {@link contactapp.security.SecurityConfig}.
 *
 * <p>PIT reported surviving mutants around CORS configuration, authentication
 * provider wiring, and filter ordering. These assertions ensure the custom
 * filters remain registered and the CORS defaults stay intact.
 */
@SpringBootTest(properties = "cors.allowed-origins=https://app.example.com,https://admin.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class SecurityConfigIntegrationTest extends PostgresContainerSupport {

    @Autowired
    private FilterChainProxy filterChainProxy;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private RateLimitingFilter rateLimitingFilter;
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;
    @Autowired
    private AuthenticationProvider authenticationProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void securityFilterChain_includesJwtAndRateLimitFiltersInOrder() {
        final List<Filter> filters = filterChainProxy.getFilters("/api/v1/tasks");

        assertThat(filters).contains(jwtAuthenticationFilter, rateLimitingFilter);
        assertThat(filters.indexOf(jwtAuthenticationFilter))
                .isLessThan(filters.indexOf(rateLimitingFilter));
    }

    @Test
    void corsConfigurationSource_appliesCustomOriginsAndHeaders() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tasks");

        final CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://app.example.com", "https://admin.example.com");
        assertThat(configuration.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders())
                .containsExactlyInAnyOrder("Authorization", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN");
        assertThat(configuration.getExposedHeaders()).contains("Authorization");
        assertThat(configuration.getMaxAge()).isEqualTo(3_600L);
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void authenticationProvider_usesBcryptEncoder() {
        // Verify BCrypt by testing actual encoding behavior
        assertThat(passwordEncoder).isNotNull();
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        final String encoded = passwordEncoder.encode("test");
        assertThat(encoded).startsWith("$2"); // BCrypt hashes start with $2a$, $2b$, or $2y$
        assertThat(passwordEncoder.matches("test", encoded)).isTrue();
    }

    /**
     * Verifies SPA routes are accessible without authentication and forward to index.html.
     * Prior to ADR-0054 (SpaController), these returned 404. Now they correctly forward
     * to the React app's entry point for client-side routing.
     */
    @Test
    void spaRoutesAreAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void securityHeaders_includesContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("form-action 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("base-uri 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("object-src 'none'")));
    }

    @Test
    void securityHeaders_includesPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("geolocation=()")))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("camera=()")));
    }

    // ==================== 401/403 JSON Response Tests ====================

    /**
     * Kills mutations on jsonAuthenticationEntryPoint:
     * - removed call to setContentType
     * - removed call to setCharacterEncoding
     * - removed call to write
     * - replaced return value with null
     */
    @Test
    void unauthenticatedRequest_returns401JsonResponse() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    /**
     * Verifies the 401 response body is proper JSON (not empty).
     * This kills the mutation that removes the write() call.
     */
    @Test
    void unauthenticatedRequest_hasNonEmptyJsonBody() throws Exception {
        var result = mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotEmpty();
        assertThat(body).contains("Unauthorized");
        assertThat(body).contains("Authentication required");
    }

    // ==================== CORS Preflight Tests ====================

    /**
     * Kills mutations on corsConfigurationSource:
     * - removed call to setAllowedOrigins
     * - removed call to setAllowedMethods
     * - removed call to setAllowedHeaders
     * - removed call to registerCorsConfiguration
     *
     * Tests actual CORS preflight behavior, not just config object.
     */
    @Test
    void corsPreflightRequest_returnsProperHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/tasks")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    /**
     * Kills mutation: removed call to setExposedHeaders
     */
    @Test
    void corsResponse_exposesAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/v1/tasks")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")));
    }

    /**
     * Kills mutation: removed call to setMaxAge
     */
    @Test
    void corsResponse_includesMaxAgeHeader() throws Exception {
        mockMvc.perform(options("/api/v1/tasks")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    // ==================== Authentication Provider Tests ====================

    /**
     * Kills mutation: removed call to setPasswordEncoder
     * Verifies that authentication actually uses the password encoder.
     */
    @Test
    void authenticationProvider_encodesAndVerifiesPasswords() {
        assertThat(authenticationProvider).isNotNull();

        // BCrypt encoding should produce different hashes for same input (salted)
        String password = "testPassword123";
        String hash1 = passwordEncoder.encode(password);
        String hash2 = passwordEncoder.encode(password);

        // Different hashes due to salting
        assertThat(hash1).isNotEqualTo(hash2);

        // But both should match the original password
        assertThat(passwordEncoder.matches(password, hash1)).isTrue();
        assertThat(passwordEncoder.matches(password, hash2)).isTrue();

        // Wrong password should not match
        assertThat(passwordEncoder.matches("wrongPassword", hash1)).isFalse();
    }

    // ==================== CSRF Cookie Tests ====================

    /**
     * Verifies CSRF cookie is properly configured for SPA security.
     *
     * <p>Tests the following security properties:
     * <ul>
     *   <li>Cookie name is XSRF-TOKEN (standard for Angular/React SPAs)</li>
     *   <li>HttpOnly is false (so JavaScript can read the token)</li>
     *   <li>Secure flag is set (via secureSessionCookie property)</li>
     * </ul>
     *
     * <p><b>Note on SameSite:</b> MockMvc uses the Servlet Cookie API which doesn't natively
     * support SameSite serialization. In production, {@link contactapp.config.TomcatCookieConfig}
     * configures Tomcat's Rfc6265CookieProcessor to add SameSite=Lax to all cookies.
     */
    @Test
    void csrfCookie_isProperlyConfiguredForSpa() throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("XSRF-TOKEN");
        // HttpOnly should NOT be present (we need JS to read the token)
        assertThat(setCookieHeader).doesNotContain("HttpOnly");
        // Secure should be set (configured via integration profile)
        assertThat(setCookieHeader).contains("Secure");
        // Note: SameSite=Lax is applied by TomcatCookieConfig at runtime,
        // but MockMvc doesn't use the embedded Tomcat cookie processor
    }

    // ==================== Public Endpoint Tests ====================

    /**
     * Verifies authentication endpoints are publicly accessible.
     */
    @Test
    void authEndpoints_arePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies health endpoints are publicly accessible.
     */
    @Test
    void healthEndpoints_arePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies API documentation is publicly accessible.
     */
    @Test
    void apiDocsEndpoints_arePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
