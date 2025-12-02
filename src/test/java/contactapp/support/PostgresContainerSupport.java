package contactapp.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres support for Spring Boot tests.
 *
 * <p>Using a single static container with reuse enabled keeps SpringBootTest/MockMvc suites
 * aligned with the production Postgres dialect while avoiding cross-test interference.
 * The container starts once and is shared across ALL test classes.
 */
public abstract class PostgresContainerSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
