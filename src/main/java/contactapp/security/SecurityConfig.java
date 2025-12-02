package contactapp.security;

import contactapp.config.RateLimitingFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for JWT-based authentication.
 *
 * <p>Security features per ADR-0018:
 * <ul>
 *   <li>Stateless session management (JWT tokens)</li>
 *   <li>CSRF tokens for browser routes while API endpoints rely on JWT</li>
 *   <li>BCrypt password encoding</li>
 *   <li>Method-level security via @PreAuthorize</li>
 *   <li>CORS configured for SPA origin</li>
 *   <li>Security headers (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy)</li>
 * </ul>
 *
 * <h2>Public Endpoints</h2>
 * <ul>
 *   <li>/api/auth/** - Authentication (login, register)</li>
 *   <li>/actuator/health, /actuator/info - Health checks</li>
 *   <li>/swagger-ui/**, /v3/api-docs/** - API documentation</li>
 *   <li>Static resources (/, /*.html, /assets/**) - SPA files</li>
 * </ul>
 *
 * <h2>Protected Endpoints</h2>
 * <ul>
 *   <li>/api/v1/** - All CRUD operations (require valid JWT)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final long DEFAULT_CORS_MAX_AGE_SECONDS = 3600L;
    private static final RequestMatcher API_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    private static final RequestMatcher ACTUATOR_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/actuator/**");
    private static final String[] ACTUATOR_PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info"
    };
    /**
     * Matches SPA route requests (GETs that do not target /api/** or /actuator/**) so deep links and refreshes can
     * load the bundle without requiring an existing JWT. This matcher is combined with the static resource rules to
     * cover all frontend routes served from the same origin.
     */
    private static final RequestMatcher SPA_GET_MATCHER = new AndRequestMatcher(
            new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/**"),
                    request -> HttpMethod.GET.matches(request.getMethod())
            ),
            new NegatedRequestMatcher(new OrRequestMatcher(API_MATCHER, ACTUATOR_MATCHER))
    );
    // CSRF disabled only for endpoints that don't need protection:
    // - actuator/swagger: not cookie-authenticated
    // - GET /api/auth/csrf-token: bootstrap call that hands the SPA its token
    // All other /api/** endpoints require CSRF token (X-XSRF-TOKEN header)
    private static final RequestMatcher CSRF_IGNORED_MATCHERS = new OrRequestMatcher(
            ACTUATOR_MATCHER,
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"),
            new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/api/auth/csrf-token"),
                    request -> HttpMethod.GET.matches(request.getMethod())
            )
    );

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;
    @Value("${server.servlet.session.cookie.secure:true}")
    private boolean secureSessionCookie;
    @Value("${csp.relaxed:false}")
    private boolean relaxedCsp;
    @Value("${security.require-ssl:false}")
    private boolean requireSsl;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring Security configuration stores filter beans; "
                    + "these are framework-managed singletons with controlled lifecycle")
    public SecurityConfig(final JwtAuthenticationFilter jwtAuthFilter,
                          final RateLimitingFilter rateLimitingFilter,
                          final UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @SuppressWarnings("deprecation") // requiresChannel deprecated in Spring Security 6.1; kept for SSL config
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        try {
            final CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
            csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                    .sameSite("Lax")
                    .secure(secureSessionCookie));

            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf
                            .ignoringRequestMatchers(CSRF_IGNORED_MATCHERS)
                            .csrfTokenRepository(csrfTokenRepository))
                    .headers(headers -> {
                        headers.contentSecurityPolicy(csp -> csp
                                .policyDirectives(buildCspPolicy()));
                        headers.permissionsPolicyHeader(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=(), "
                                        + "payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()"));
                        headers.contentTypeOptions(contentType -> { });
                        headers.frameOptions(frame -> frame.sameOrigin());
                        headers.referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    })
                    .authorizeHttpRequests(auth -> auth
                            // Public endpoints - authentication
                            .requestMatchers("/api/auth/**").permitAll()
                            // Public endpoints - health/monitoring
                            .requestMatchers(ACTUATOR_PUBLIC_ENDPOINTS).permitAll()
                            // Public endpoints - API documentation
                            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                            // Public endpoints - static resources (SPA)
                            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                            // Protected endpoints - all API CRUD operations require authentication
                            .requestMatchers("/api/v1/**").authenticated()
                            // Public SPA routes (React router) - allow GET so the bundle can load even without JWT
                            .requestMatchers(SPA_GET_MATCHER).permitAll()
                            .requestMatchers("/error").permitAll()
                            // Any other request also requires authentication
                            .anyRequest().authenticated()
                    );

            // Require HTTPS in production when configured
            if (requireSsl) {
                http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
            }

            http
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )
                    .authenticationProvider(authenticationProvider())
                    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                    // Rate limiting filter runs AFTER JWT auth so SecurityContext is populated
                    .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class);

            return http.build();
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to configure security filter chain", ex);
        }
    }

    /**
     * Configures CORS for the SPA origin.
     *
     * <p>Allows the React frontend (running on a different port during development)
     * to make API requests. In production, the SPA is served from the same origin
     * so CORS is less critical, but we keep it configured for flexibility.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-XSRF-TOKEN"
        ));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(DEFAULT_CORS_MAX_AGE_SECONDS);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private List<String> parseAllowedOrigins() {
        return Arrays.asList(allowedOrigins.split(","));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config) {
        try {
            return config.getAuthenticationManager();
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to obtain authentication manager", ex);
        }
    }

    /**
     * Builds the Content Security Policy based on the csp.relaxed configuration.
     *
     * <p>In development mode (csp.relaxed=true), allows 'unsafe-inline' for styles
     * to support Vite's hot module replacement and CSS injection. In production
     * (csp.relaxed=false), uses a strict CSP policy.
     *
     * @return the CSP policy string
     */
    private String buildCspPolicy() {
        if (relaxedCsp) {
            // Relaxed CSP for development with Vite HMR support
            return "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob:; "
                    + "font-src 'self' data:; "
                    + "connect-src 'self' ws: wss:; "
                    + "frame-ancestors 'self'; "
                    + "form-action 'self'; "
                    + "base-uri 'self'; "
                    + "object-src 'none'";
        }
        // Strict CSP for production
        return "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self'; "
                + "connect-src 'self'; "
                + "frame-ancestors 'self'; "
                + "form-action 'self'; "
                + "base-uri 'self'; "
                + "object-src 'none'";
    }
}
