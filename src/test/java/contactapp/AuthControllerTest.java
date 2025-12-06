package contactapp;

import contactapp.config.RateLimitingFilter;
import contactapp.security.Role;
import contactapp.security.User;
import contactapp.security.UserRepository;
import contactapp.support.SecuredMockMvcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link contactapp.api.AuthController}.
 *
 * <p>Tests REST endpoints for authentication operations including:
 * <ul>
 *   <li>Happy path for login and registration</li>
 *   <li>Validation errors (Bean Validation layer)</li>
 *   <li>Invalid credentials (401)</li>
 *   <li>Duplicate username/email conflicts (409)</li>
 * </ul>
 *
 * @see contactapp.api.AuthController
 */
class AuthControllerTest extends SecuredMockMvcTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        // Clear rate limit buckets between tests to prevent 429 collisions
        rateLimitingFilter.clearBuckets();
    }

    // ==================== Registration Happy Path Tests ====================

    @Test
    void register_validRequest_returns201WithCookie() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "newuser",
                                "email": "newuser@example.com",
                                "password": "securePassword123"
                            }
                            """))
                .andExpect(status().isCreated())
                // Token is in HttpOnly cookie, not response body (ADR-0043)
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                .andExpect(jsonPath("$.token").doesNotExist()) // null serialized as absent
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresIn").value(greaterThan(0)));
    }

    @Test
    void register_validRequest_userPersistedInDatabase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "persisteduser",
                                "email": "persisted@example.com",
                                "password": "securePassword123"
                            }
                            """))
                .andExpect(status().isCreated());

        // Verify user was persisted
        var user = userRepository.findByUsername("persisteduser");
        assertThat(user).isPresent();
        assertThat(user.get().getEmail()).isEqualTo("persisted@example.com");
        assertThat(user.get().getRole()).isEqualTo(Role.USER);
    }

    // ==================== Login Happy Path Tests ====================

    @Test
    void login_validCredentials_returns200WithCookie() throws Exception {
        // Create a user first
        createTestUser("loginuser", "login@example.com", "correctPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "loginuser",
                                "password": "correctPassword123"
                            }
                            """))
                .andExpect(status().isOk())
                // Token is in HttpOnly cookie, not response body (ADR-0043)
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                .andExpect(jsonPath("$.token").doesNotExist()) // null serialized as absent
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresIn").value(greaterThan(0)));
    }

    @Test
    void login_adminUser_returnsAdminRole() throws Exception {
        createTestUser("adminuser", "admin@example.com", "adminPassword123", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "adminuser",
                                "password": "adminPassword123"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ==================== Login Failure Tests ====================

    @Test
    void login_wrongPassword_returns401() throws Exception {
        createTestUser("testuser", "test@example.com", "correctPassword");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "testuser",
                                "password": "wrongPassword"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_nonexistentUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "doesnotexist",
                                "password": "anyPassword123"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    // ==================== Registration Conflict Tests ====================

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        createTestUser("existinguser", "existing@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "existinguser",
                                "email": "different@example.com",
                                "password": "Password123456"
                            }
                            """))
                .andExpect(status().isConflict())
                // Generic message prevents user enumeration (ADR-0038)
                .andExpect(jsonPath("$.message").value("Username or email already exists"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        createTestUser("user1", "duplicate@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "differentuser",
                                "email": "duplicate@example.com",
                                "password": "Password123456"
                            }
                            """))
                .andExpect(status().isConflict())
                // Generic message prevents user enumeration (ADR-0038)
                .andExpect(jsonPath("$.message").value("Username or email already exists"));
    }

    // ==================== Validation Error Tests (Registration) ====================

    @ParameterizedTest(name = "register with invalid {0}")
    @MethodSource("invalidRegisterInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void register_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        assertThat(fieldName).isNotBlank();
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidRegisterInputs() {
        return Stream.of(
                // Username validation
                Arguments.of("username (blank)", """
                    {"username": "", "email": "test@example.com", "password": "Password123"}
                    """, "username"),
                Arguments.of("username (too long)", """
                    {"username": "a".repeat(51), "email": "test@example.com", "password": "Password123"}
                    """.replace("\"a\".repeat(51)", "\"" + "a".repeat(51) + "\""), "username"),

                // Email validation
                Arguments.of("email (blank)", """
                    {"username": "testuser", "email": "", "password": "Password123"}
                    """, "email"),
                Arguments.of("email (invalid format)", """
                    {"username": "testuser", "email": "notanemail", "password": "Password123"}
                    """, "email"),

                // Password validation - length
                Arguments.of("password (blank)", """
                    {"username": "testuser", "email": "test@example.com", "password": ""}
                    """, "password"),
                Arguments.of("password (too short)", """
                    {"username": "testuser", "email": "test@example.com", "password": "Short1"}
                    """, "password"),

                // Password validation - strength (must have uppercase, lowercase, digit)
                Arguments.of("password (no uppercase)", """
                    {"username": "testuser", "email": "test@example.com", "password": "password123"}
                    """, "password must contain"),
                Arguments.of("password (no lowercase)", """
                    {"username": "testuser", "email": "test@example.com", "password": "PASSWORD123"}
                    """, "password must contain"),
                Arguments.of("password (no digit)", """
                    {"username": "testuser", "email": "test@example.com", "password": "PasswordABC"}
                    """, "password must contain")
        );
    }

    // ==================== Validation Error Tests (Login) ====================

    @ParameterizedTest(name = "login with invalid {0}")
    @MethodSource("invalidLoginInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void login_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        assertThat(fieldName).isNotBlank();
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidLoginInputs() {
        return Stream.of(
                Arguments.of("username (blank)", """
                    {"username": "", "password": "password123"}
                    """, "username"),
                Arguments.of("password (blank)", """
                    {"username": "testuser", "password": ""}
                    """, "password")
        );
    }

    // ==================== Malformed JSON Tests ====================

    @Test
    void register_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    @Test
    void login_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    // ==================== Boundary Tests ====================

    @Test
    void register_usernameAtMaxLength_accepted() throws Exception {
        final String maxUsername = "a".repeat(50);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "username": "%s",
                                "email": "maxlen@example.com",
                                "password": "Password123456"
                            }
                            """, maxUsername)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(maxUsername));
    }

    @Test
    void register_passwordAtMinLength_accepted() throws Exception {
        // 8 chars minimum with required uppercase, lowercase, and digit
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "minpwuser",
                                "email": "minpw@example.com",
                                "password": "Pass1234"
                            }
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    void csrfTokenEndpoint_returnsToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // ==================== Token Refresh Tests ====================

    @Test
    void refresh_withValidToken_returns200WithNewToken() throws Exception {
        // First, login to get valid cookies (auth_token and refresh_token)
        createTestUser("refreshuser", "refresh@example.com", "Password123");

        final var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "refreshuser",
                                "password": "Password123"
                            }
                            """))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the refresh_token cookie from login response (ADR-0052 Phase B)
        final var refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        // Now call refresh with the refresh_token cookie
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("refreshuser"))
                .andExpect(jsonPath("$.email").value("refresh@example.com"))
                .andExpect(jsonPath("$.expiresIn").value(greaterThan(0)))
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void refresh_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withInvalidToken_returns401() throws Exception {
        final var invalidCookie = new jakarta.servlet.http.Cookie("auth_token", "invalid.token.here");

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(invalidCookie))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Logout Tests ====================

    @Test
    void logout_returns204AndClearsCookies() throws Exception {
        // First, login to get valid cookies
        createTestUser("logoutuser", "logout@example.com", "Password123");

        final var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "logoutuser",
                                "password": "Password123"
                            }
                            """))
                .andExpect(status().isOk())
                .andReturn();

        final var authCookie = loginResult.getResponse().getCookie("auth_token");
        final var refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(authCookie).isNotNull();
        assertThat(refreshCookie).isNotNull();

        // Now logout - should clear cookies
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(authCookie)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                // Cookies should be cleared (maxAge=0)
                .andExpect(cookie().maxAge("auth_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void logout_withoutCookies_stillReturns204() throws Exception {
        // Logout without any cookies should still succeed (idempotent)
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_revokesRefreshToken_preventingReuse() throws Exception {
        // First, login to get valid cookies
        createTestUser("revokeuser", "revoke@example.com", "Password123");

        final var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "revokeuser",
                                "password": "Password123"
                            }
                            """))
                .andExpect(status().isOk())
                .andReturn();

        final var refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        // Logout to revoke the refresh token
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent());

        // Attempt to use the revoked refresh token - should fail
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutRefreshCookie_stillRevokesTokens() throws Exception {
        createTestUser("legacy-path", "legacy@example.com", "Password123");

        final var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "legacy-path",
                                "password": "Password123"
                            }
                            """))
                .andExpect(status().isOk())
                .andReturn();

        final var authCookie = loginResult.getResponse().getCookie("auth_token");
        final var refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        // Fingerprint cookie is needed for the JWT to be validated
        final var fgpCookie = loginResult.getResponse().getCookie("Fgp");
        assertThat(authCookie).isNotNull();
        assertThat(refreshCookie).isNotNull();
        assertThat(fgpCookie).isNotNull();

        // Simulate legacy path scoping where refresh_token is not sent to /logout
        // but fingerprint cookie IS sent (required for JWT validation)
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(authCookie, fgpCookie))
                .andExpect(status().isNoContent());

        // Refresh token should be revoked even though it was not sent on logout
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Cookie Security Attribute Tests ====================

    @Test
    void login_setsHttpOnlyCookies() throws Exception {
        createTestUser("cookieuser", "cookie@example.com", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "cookieuser",
                                "password": "Password123"
                            }
                            """))
                .andExpect(status().isOk())
                // Verify auth_token cookie attributes
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                // Verify refresh_token cookie exists
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    // ==================== Helper Methods ====================

    private void createTestUser(
            final String username,
            final String email,
            final String rawPassword) {
        createTestUser(username, email, rawPassword, Role.USER);
    }

    private void createTestUser(
            final String username,
            final String email,
            final String rawPassword,
            final Role role) {
        final String encodedPassword = passwordEncoder.encode(rawPassword);
        final User user = new User(username, email, encodedPassword, role);
        userRepository.save(user);
    }
}
