package contactapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import contactapp.support.PostgresContainerSupport;

/**
 * Smoke test verifying the Spring application context loads successfully.
 *
 * <p>This test catches configuration errors early:
 * <ul>
 *   <li>Missing or misconfigured beans</li>
 *   <li>Circular dependency issues</li>
 *   <li>Invalid property bindings</li>
 *   <li>Component scanning failures</li>
 * </ul>
 *
 * <p>If this test fails, the application will not start in production.
 *
 * <p>The {@link SpringBootTest#webEnvironment()} is set to {@code MOCK} so the
 * embedded Tomcat server does not attempt to bind to a privileged port inside
 * constrained CI environments. We only need to know that the beans wire up; the
 * full HTTP stack is exercised by the controller tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
class ApplicationTest extends PostgresContainerSupport {

    /**
     * Verifies the application context loads without throwing exceptions.
     *
     * <p>An empty test body is intentional: @SpringBootTest triggers context
     * loading before any test method runs. If the context fails to load,
     * this test fails automatically with detailed error messages.
     */
    @Test
    void contextLoads() {
        // Context loading is the test - no assertions needed.
        // Spring will throw if beans cannot be wired or configuration is invalid.
    }

    /**
     * Covers the main() method for JaCoCo line coverage.
     *
     * <p>Calls Application.main() directly to ensure the entrypoint is exercised.
     * Since @SpringBootTest already starts a context, this just verifies the
     * main method itself is reachable. Spring Boot handles duplicate context
     * gracefully in test mode.
     */
    @Test
    void mainMethodCoverage() {
        // Disable the web stack so constrained CI agents do not need to bind any sockets.
        Application.main(new String[]{"--spring.main.web-application-type=none"});
    }
}
