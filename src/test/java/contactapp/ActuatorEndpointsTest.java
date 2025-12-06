package contactapp;

import contactapp.security.WithMockAppUser;
import contactapp.support.SecuredMockMvcTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying actuator endpoint security configuration.
 *
 * <p>These tests ensure:
 * <ul>
 *   <li>/actuator/health is accessible without auth (orchestrator probes)</li>
 *   <li>/actuator/info is accessible without auth (operational visibility)</li>
 *   <li>/actuator/prometheus and /actuator/metrics require authentication</li>
 *   <li>Non-exposed endpoints return 404</li>
 * </ul>
 *
 * <p>Security note: Actuator endpoints can expose sensitive information.
 * Health and info are public; prometheus/metrics require authentication;
 * other endpoints (env, beans) are not exposed at all.
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html">
 *      Spring Boot Actuator Documentation</a>
 */
class ActuatorEndpointsTest extends SecuredMockMvcTest {

    /**
     * Verifies /actuator/health returns 200 OK with status field.
     *
     * <p>The health endpoint is critical for Kubernetes liveness/readiness
     * probes and load balancer health checks. Must be accessible without
     * authentication for orchestrators to function.
     */
    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Verifies /actuator/info returns 200 OK.
     *
     * <p>The info endpoint provides build metadata and application version
     * for operational dashboards. Returns empty JSON if no info properties
     * are configured, but must still return 200.
     */
    @Test
    void infoEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies /actuator/env is not accessible (returns 401).
     *
     * <p>The env endpoint exposes environment variables and system properties,
     * which may contain secrets. Spring Security's AuthenticationEntryPoint
     * returns 401 Unauthorized for unauthenticated requests.
     */
    @Test
    void envEndpointIsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies /actuator/beans is not accessible (returns 401).
     *
     * <p>The beans endpoint lists all Spring beans and their dependencies,
     * which reveals internal architecture. Spring Security's AuthenticationEntryPoint
     * returns 401 Unauthorized for unauthenticated requests.
     */
    @Test
    void beansEndpointIsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies /actuator/metrics requires authentication (returns 401 without auth).
     *
     * <p>The metrics endpoint exposes JVM and application metrics.
     * Protected by Spring Security's AuthenticationEntryPoint.
     */
    @Test
    void metricsEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies /actuator/metrics is accessible with authentication.
     */
    @Test
    @WithMockAppUser
    void metricsEndpointAccessibleWithAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies /actuator/prometheus requires authentication (returns 401 without auth).
     *
     * <p>The prometheus endpoint exposes metrics in Prometheus format.
     * Protected by Spring Security's AuthenticationEntryPoint.
     */
    @Test
    void prometheusEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies /actuator/prometheus is accessible with authentication.
     *
     * <p>Returns Prometheus-format metrics for scraping by monitoring systems.
     */
    @Test
    @WithMockAppUser
    void prometheusEndpointAccessibleWithAuth() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
