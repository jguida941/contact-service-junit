package contactapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable Spring's scheduling support.
 *
 * <p>Required for {@code @Scheduled} annotations to work, such as:
 * <ul>
 *   <li>Refresh token cleanup job (ADR-0052 Phase B)</li>
 *   <li>Any future scheduled maintenance tasks</li>
 * </ul>
 *
 * <p>Scheduling is enabled application-wide. Individual scheduled methods
 * are defined in their respective service classes.
 *
 * @see org.springframework.scheduling.annotation.Scheduled
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Configuration class - enables @Scheduled annotation processing
}
