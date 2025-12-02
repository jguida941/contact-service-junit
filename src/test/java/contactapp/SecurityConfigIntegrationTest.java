package contactapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import contactapp.config.RateLimitingFilter;
import contactapp.security.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
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

    @Test
    void spaRoutesAreAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/contacts"))
                .andExpect(status().isNotFound());
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
                        org.hamcrest.Matchers.containsString("frame-ancestors 'self'")))
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
}
