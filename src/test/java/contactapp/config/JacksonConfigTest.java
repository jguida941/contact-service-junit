package contactapp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ensures {@link JacksonConfig} disables string coercion so schema violations surface as 400s.
 */
class JacksonConfigTest {

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        JacksonConfig config = new JacksonConfig();
        JsonMapperBuilderCustomizer customizer = config.strictCoercionCustomizer();
        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);
        mapper = builder.build();
    }

    @Test
    void jsonMapperIsConfigured() {
        assertThat(mapper).isNotNull();
    }

    @Test
    void objectMapperRejectsBooleanCoercion() throws Exception {
        assertThat(mapper).isNotNull();

        String payload = """
                {"value": false}
                """;

        assertThatThrownBy(() -> mapper.readValue(payload, SamplePayload.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void objectMapperRejectsNumericCoercion() {
        String payload = """
                {"value": 42}
                """;

        assertThatThrownBy(() -> mapper.readValue(payload, SamplePayload.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    private static final class SamplePayload {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }
    }
}
