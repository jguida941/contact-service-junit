package contactapp.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers Postgres support for Spring Boot tests.
 *
 * <p>Using a single static container keeps SpringBootTest/MockMvc suites aligned
 * with the production Postgres dialect while avoiding cross-test interference.
 */
@Testcontainers
public abstract class PostgresContainerSupport {

    @Container
    @ServiceConnection
    // Postgres 16 to mirror dev/prod environments.
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
