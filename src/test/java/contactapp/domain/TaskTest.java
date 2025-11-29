package contactapp.domain;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Task}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Trimming and successful construction</li>
 *   <li>Update semantics (atomic replacement of fields and unchanged state on validation failure)</li>
 *   <li>Setter behavior for valid values</li>
 *   <li>Validation of taskId, name, and description in constructor, setters, and update(...)</li>
 * </ul>
 *
 * <p>Tests are in the same package as Task to access package-private methods.
 */
public class TaskTest {

    /**
     * Ensures constructor trimming works for every field.
     */
    @Test
    void testSuccessfulCreationTrimsValues() {
        Task task = new Task(" 123 ", "  Write tests ", "  Cover edge cases ");

        assertThat(task.getTaskId()).isEqualTo("123");
        assertThat(task.getName()).isEqualTo("Write tests");
        assertThat(task.getDescription()).isEqualTo("Cover edge cases");
    }

    /**
     * Confirms {@link Task#update(String, String)} swaps both mutable fields in one operation.
     */
    @Test
    void testUpdateReplacesValuesAtomically() {
        Task task = new Task("100", "Draft plan", "Outline initial work");

        task.update("Implement feature", "Finish Task entity and service");

        assertThat(task.getName()).isEqualTo("Implement feature");
        assertThat(task.getDescription()).isEqualTo("Finish Task entity and service");
    }

    /**
     * Validates that setters accept good data and persist changes.
     */
    @Test
    void testSettersAcceptValidValues() {
        Task task = new Task("100", "Draft plan", "Outline initial work");

        task.setName("Write docs");
        task.setDescription("Document Task behavior");

        assertThat(task.getName()).isEqualTo("Write docs");
        assertThat(task.getDescription()).isEqualTo("Document Task behavior");
    }

    /**
     * Supplies invalid constructor inputs for {@link #testConstructorValidation}.
     */
    @CsvSource(value = {
            // taskId validation
            "' ', name, description, 'taskId must not be null or blank'",
            "'', name, description, 'taskId must not be null or blank'",
            "null, name, description, 'taskId must not be null or blank'",
            "12345678901, name, description, 'taskId length must be between 1 and 10'",

            // name validation
            "1, ' ', description, 'name must not be null or blank'",
            "1, '', description, 'name must not be null or blank'",
            "1, null, description, 'name must not be null or blank'",
            "1, This name is definitely too long, description, 'name length must be between 1 and 20'",

            // description validation
            "1, name, ' ', 'description must not be null or blank'",
            "1, name, '', 'description must not be null or blank'",
            "1, name, null, 'description must not be null or blank'",
            "1, name, 'This description is intentionally made way too long to exceed the fifty character limit set', 'description length must be between 1 and 50'"
    }, nullValues = "null")

    /**
     * Ensures invalid constructor inputs raise {@link IllegalArgumentException} with the expected message.
     */
    @ParameterizedTest
    void testConstructorValidation(
            String taskId,
            String name,
            String description,
            String expectedMessage) {
        assertThatThrownBy(() -> new Task(taskId, name, description))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Drives invalid name values for {@link #testSetNameValidation}.
     */
    @CsvSource(value = {
            "' ', 'name must not be null or blank'",
            "'', 'name must not be null or blank'",
            "null, 'name must not be null or blank'",
            "This name is definitely too long, 'name length must be between 1 and 20'",
    }, nullValues = "null")

    /**
     * Verifies {@link Task#setName(String)} rejects blank/null/over-length names.
     */
    @ParameterizedTest
    void testSetNameValidation(String invalidName, String expectedMessage) {
        Task task = new Task("100", "Valid", "Valid description");

        assertThatThrownBy(() -> task.setName(invalidName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Supplies invalid descriptions for {@link #testSetDescriptionValidation}.
     */
    @CsvSource(value = {
            "' ', 'description must not be null or blank'",
            "'', 'description must not be null or blank'",
            "null, 'description must not be null or blank'",
            "'This description is intentionally made way too long to exceed the fifty character limit set', 'description length must be between 1 and 50'"
    }, nullValues = "null")

    /**
     * Ensures {@link Task#setDescription(String)} enforces the required constraints.
     */
    @ParameterizedTest
    void testSetDescriptionValidation(String invalidDescription, String expectedMessage) {
        Task task = new Task("100", "Valid", "Valid description");

        assertThatThrownBy(() -> task.setDescription(invalidDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Data for update(...) inputs we expect to fail.
     *
     * <p>Each Arguments.of(name, description, message) row feeds one run of
     * testUpdateRejectsInvalidValuesAtomically.
     */
    private static Stream<Arguments> invalidUpdateInputs() {
        return Stream.of(
                Arguments.of(" ", "Valid description", "name must not be null or blank"),
                Arguments.of("", "Valid description", "name must not be null or blank"),
                Arguments.of(null, "Valid description", "name must not be null or blank"),
                Arguments.of("This name is definitely too long", "Valid description", "name length must be between 1 and 20"),
                Arguments.of("Valid", " ", "description must not be null or blank"),
                Arguments.of("Valid", "", "description must not be null or blank"),
                Arguments.of("Valid", null, "description must not be null or blank"),
                Arguments.of("Valid", "This description is intentionally made way too long to exceed the fifty character limit set", "description length must be between 1 and 50")
        );
    }

    /**
     * update(...) must:
     * <ul>
     *   <li>reject invalid name/description with the correct error message</li>
     *   <li>leave the existing Task state unchanged when validation fails (atomic)</li>
     * </ul>
     */
    @ParameterizedTest
    @MethodSource("invalidUpdateInputs")
    void testUpdateRejectsInvalidValuesAtomically(
            String newName,
            String newDescription,
            String expectedMessage) {
        Task task = new Task("100", "Draft plan", "Outline initial work");

        assertThatThrownBy(() -> task.update(newName, newDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        assertThat(task)
                .hasFieldOrPropertyWithValue("name", "Draft plan")
                .hasFieldOrPropertyWithValue("description", "Outline initial work");
    }

    /**
     * Ensures the copy guard rejects corrupted state (null internal fields).
     *
     * <p>Added to kill PITest mutant: "removed call to validateCopySource" in Task.copy().
     * This test uses reflection to corrupt each internal field and verify copy() throws.
     * Parameterized to achieve full branch coverage of the validateCopySource null checks.
     */
    @ParameterizedTest(name = "copy rejects null {0}")
    @MethodSource("nullFieldProvider")
    void testCopyRejectsNullInternalState(String fieldName) throws Exception {
        Task task = new Task("901", "Valid Name", "Valid Description");

        // Use reflection to corrupt internal state (simulate memory corruption or serialization bugs)
        Field field = Task.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(task, null);

        assertThatThrownBy(task::copy)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("task copy source must not be null");
    }

    /**
     * Provides field names for the null internal state test.
     */
    static Stream<String> nullFieldProvider() {
        return Stream.of("taskId", "name", "description");
    }
}
