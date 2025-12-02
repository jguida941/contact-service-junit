package contactapp.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Jackson configuration to enforce strict type checking.
 *
 * <p>By default, Jackson coerces non-string types to strings (e.g., {@code false} → {@code "false"}).
 * This configuration disables that behavior to ensure API requests match the OpenAPI schema exactly.
 *
 * <h2>Why This Matters</h2>
 * <p>Without this configuration:
 * <ul>
 *   <li>{@code {"address": false}} would be accepted as {@code {"address": "false"}}</li>
 *   <li>{@code {"description": 123}} would be accepted as {@code {"description": "123"}}</li>
 * </ul>
 *
 * <p>This violates the OpenAPI contract which declares these fields as strings.
 * API fuzzing tools like Schemathesis catch this as "API accepted schema-violating request".
 *
 * @see <a href="https://github.com/FasterXML/jackson-databind/issues/3013">Jackson coercion config</a>
 */
@Configuration
public class JacksonConfig {

    /**
     * Customizes Jackson 3 JsonMapper to reject type coercion for string fields.
     *
     * <p>This ensures that boolean, integer, and float values are not silently
     * converted to strings, enforcing strict schema compliance.
     *
     * @return customizer for the JsonMapper builder
     */
    @Bean
    public JsonMapperBuilderCustomizer strictCoercionCustomizer() {
        return builder -> configureStrictCoercion(builder);
    }

    private void configureStrictCoercion(final JsonMapper.Builder builder) {
        // Configure coercion to fail when non-string values are provided for String fields
        // This enforces strict schema compliance per OpenAPI contract
        builder.withCoercionConfig(LogicalType.Textual, config -> {
            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        });
    }
}
