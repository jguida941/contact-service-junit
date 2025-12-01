package contactapp.security;

import contactapp.config.RateLimitingFilter;
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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
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
 *   <li>CSRF disabled (stateless API with JWT)</li>
 *   <li>BCrypt password encoding</li>
 *   <li>Method-level security via @PreAuthorize</li>
 *   <li>CORS configured for SPA origin</li>
 *   <li>Security headers (X-Content-Type-Options, X-Frame-Options, Referrer-Policy)</li>
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
    private static final RequestMatcher API_MATCHER = new AntPathRequestMatcher("/api/**");
    private static final RequestMatcher ACTUATOR_MATCHER = new AntPathRequestMatcher("/actuator/**");
    private static final String[] ACTUATOR_PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };
    /**
     * Matches SPA route requests (GETs that do not target /api/** or /actuator/**) so deep links and refreshes can
     * load the bundle without requiring an existing JWT. This matcher is combined with the static resource rules to
     * cover all frontend routes served from the same origin.
     */
    private static final RequestMatcher SPA_GET_MATCHER = new AndRequestMatcher(
            new AntPathRequestMatcher("/**", HttpMethod.GET.name()),
            new NegatedRequestMatcher(new OrRequestMatcher(API_MATCHER, ACTUATOR_MATCHER))
    );

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    public SecurityConfig(final JwtAuthenticationFilter jwtAuthFilter,
                          final RateLimitingFilter rateLimitingFilter,
                          final UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> { })
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
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
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limiting filter runs AFTER JWT auth so SecurityContext is populated
                .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class);

        return http.build();
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
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
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
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
