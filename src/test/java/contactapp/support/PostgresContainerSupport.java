package contactapp.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres support for Spring Boot tests.
 *
 * <p>This creates a SINGLE container instance shared across ALL test classes in the JVM.
 * The container starts once and remains active throughout the entire test suite execution.
 * This approach aligns with Spring's test context caching, preventing connection pool
 * issues when the same @SpringBootTest configuration is reused across multiple test classes.
 *
 * <p>@ServiceConnection automatically configures Spring Boot's datasource properties
 * from the container, eliminating the need for manual @DynamicPropertySource configuration.
 * This prevents configuration conflicts that could cause Spring context initialization hangs.
 */
public abstract class PostgresContainerSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }
}
