package contactapp.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an application-wide {@link Clock} bean for time-sensitive operations.
 *
 * <p>This configuration enables proper dependency injection of time, which is critical for:
 * <ul>
 *   <li>Consistent timezone handling across the application</li>
 *   <li>Deterministic testing with fixed clocks</li>
 *   <li>Production deployments with configurable business timezones</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Set the timezone in application.yml:
 * <pre>
 * app:
 *   timezone: America/New_York
 * </pre>
 *
 * <p>If not specified, defaults to the JVM's system default zone.
 *
 * <h2>Usage in Services</h2>
 * <pre>
 * {@literal @}Service
 * public class TaskService {
 *     private final Clock clock;
 *
 *     public TaskService(TaskStore store, Clock clock) {
 *         this.clock = clock;
 *     }
 *
 *     public void createTask(LocalDate dueDate) {
 *         Validation.validateDateNotPast(dueDate, "Due Date", clock);
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @see contactapp.domain.Validation#validateDateNotPast(java.time.LocalDate, String, Clock)
 */
@Configuration
public class ClockConfig {

    /**
     * Creates a Clock bean configured for the application's business timezone.
     *
     * <p>The timezone is read from the {@code app.timezone} property. If not set,
     * uses the JVM's system default zone. For multi-tenant applications with
     * per-user timezones, inject this clock and derive user-specific clocks from it.
     *
     * @param timezone the IANA timezone ID (e.g., "America/New_York", "UTC")
     * @return a Clock in the configured timezone
     */
    @Bean
    public Clock clock(@Value("${app.timezone:}") final String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return Clock.systemDefaultZone();
        }
        return Clock.system(ZoneId.of(timezone));
    }
}
