package contactapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards against reintroducing {@code getInstance()} usage outside the legacy tests.
 * New code must rely on Spring DI, so only the explicit compatibility tests are
 * allowed to reference the singleton entry points.
 */
class LegacySingletonUsageTest {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "ContactService.getInstance(", Set.of(
                    "src/test/java/contactapp/service/ContactServiceTest.java",
                    "src/test/java/contactapp/service/ContactServiceLegacyTest.java"),
            "TaskService.getInstance(", Set.of(
                    "src/test/java/contactapp/service/TaskServiceTest.java",
                    "src/test/java/contactapp/service/TaskServiceLegacyTest.java"),
            "AppointmentService.getInstance(", Set.of(
                    "src/test/java/contactapp/service/AppointmentServiceTest.java",
                    "src/test/java/contactapp/service/AppointmentServiceLegacyTest.java"));

    @Test
    void legacySingletonUsageRestrictedToApprovedFiles() throws IOException {
        final Map<String, List<String>> violations = new LinkedHashMap<>();

        final String self = "src/test/java/contactapp/LegacySingletonUsageTest.java";

        Files.walk(Path.of("src"))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    final String normalizedPath = path.toString().replace('\\', '/');
                    if (normalizedPath.equals(self)) {
                        return;
                    }
                    final String content;
                    try {
                        content = Files.readString(path);
                    } catch (final IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                    ALLOWED.forEach((pattern, allowedPaths) -> {
                        if (content.contains(pattern) && !allowedPaths.contains(normalizedPath)) {
                            violations.computeIfAbsent(pattern, unused -> new ArrayList<>())
                                    .add(normalizedPath);
                        }
                    });
                });

        assertThat(violations)
                .withFailMessage("Legacy getInstance() references outside approved files: %s", violations)
                .isEmpty();
    }
}
