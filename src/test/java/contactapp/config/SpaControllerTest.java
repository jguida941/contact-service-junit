package contactapp.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import contactapp.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link SpaController}.
 *
 * <p>Verifies that SPA routes are correctly forwarded to index.html for client-side
 * routing while ensuring API and static resource routes remain unaffected.
 *
 * <p>Security considerations tested:
 * <ul>
 *   <li>Only GET requests are forwarded</li>
 *   <li>API routes are not intercepted</li>
 *   <li>Actuator routes are not intercepted</li>
 *   <li>Static resources (.js, .css) are not intercepted</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class SpaControllerTest extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Authentication Routes")
    class AuthenticationRoutes {

        @Test
        @DisplayName("GET /login forwards to index.html")
        void loginForwardsToIndex() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }

        @Test
        @DisplayName("GET /register forwards to index.html")
        void registerForwardsToIndex() throws Exception {
            mockMvc.perform(get("/register"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Nested
    @DisplayName("Main Application Routes")
    class MainRoutes {

        @ParameterizedTest
        @ValueSource(strings = {"/contacts", "/tasks", "/appointments", "/projects"})
        @DisplayName("Collection routes forward to index.html")
        void collectionRoutesForwardToIndex(String route) throws Exception {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/contacts/123", "/tasks/456", "/appointments/789", "/projects/abc"})
        @DisplayName("Detail routes forward to index.html")
        void detailRoutesForwardToIndex(String route) throws Exception {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Nested
    @DisplayName("Admin Routes")
    class AdminRoutes {

        @Test
        @DisplayName("GET /admin forwards to index.html")
        void adminForwardsToIndex() throws Exception {
            mockMvc.perform(get("/admin"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/admin/users", "/admin/metrics", "/admin/settings"})
        @DisplayName("Admin section routes forward to index.html")
        void adminSectionRoutesForwardToIndex(String route) throws Exception {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }

        @Test
        @DisplayName("GET /admin/users/123 forwards to index.html")
        void adminSubsectionForwardsToIndex() throws Exception {
            mockMvc.perform(get("/admin/users/123"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Nested
    @DisplayName("User Routes")
    class UserRoutes {

        @ParameterizedTest
        @ValueSource(strings = {"/profile", "/settings"})
        @DisplayName("User routes forward to index.html")
        void userRoutesForwardToIndex(String route) throws Exception {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Nested
    @DisplayName("Error Routes")
    class ErrorRoutes {

        @ParameterizedTest
        @ValueSource(strings = {"/not-found", "/unauthorized"})
        @DisplayName("Error routes forward to index.html")
        void errorRoutesForwardToIndex(String route) throws Exception {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Nested
    @DisplayName("Security: Routes NOT forwarded")
    class SecurityNotForwarded {

        @Test
        @DisplayName("POST to SPA routes is rejected (method not allowed)")
        void postToSpaRoutesRejected() throws Exception {
            // POST to /login should NOT be handled by SpaController
            // It will hit Spring Security which requires authentication
            mockMvc.perform(post("/login"))
                    .andExpect(status().isForbidden()); // CSRF protection kicks in
        }

        @Test
        @DisplayName("API routes are not intercepted by SpaController")
        void apiRoutesNotIntercepted() throws Exception {
            // API routes should hit the actual API controllers, not SpaController
            mockMvc.perform(get("/api/v1/tasks"))
                    .andExpect(status().isUnauthorized()); // Requires JWT
        }

        @Test
        @DisplayName("Actuator routes are not intercepted")
        void actuatorRoutesNotIntercepted() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk()); // Should return JSON health status
        }

        @Test
        @DisplayName("Auth API routes are not intercepted")
        void authApiRoutesNotIntercepted() throws Exception {
            mockMvc.perform(get("/api/auth/csrf-token"))
                    .andExpect(status().isOk()); // Should return CSRF token
        }
    }

    @Nested
    @DisplayName("Static Resources")
    class StaticResources {

        @Test
        @DisplayName("Root path serves index.html directly")
        void rootServesIndex() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk());
            // No forward - served directly as static resource
        }

        @Test
        @DisplayName("Assets are served directly, not forwarded")
        void assetsServedDirectly() throws Exception {
            // This tests that /assets/** URLs go to static resources
            // The actual file may not exist in test env, but it shouldn't forward
            mockMvc.perform(get("/assets/index.js"))
                    .andExpect(status().isNotFound()); // File doesn't exist in test
            // Key point: NOT forwarded to index.html
        }
    }
}
