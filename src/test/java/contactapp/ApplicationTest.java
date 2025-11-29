package contactapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTest {

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
        Application.main(new String[]{});
    }
}
