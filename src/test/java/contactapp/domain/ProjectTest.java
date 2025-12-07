package contactapp.domain;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the Project class.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Successful creation with valid data</li>
 *   <li>Updates via setters with valid data</li>
 *   <li>Invalid inputs cause IllegalArgumentException with specific validation messages</li>
 *   <li>Atomic update behavior (all-or-nothing)</li>
 * </ul>
 *
 * <p>Tests are in the same package as Project to access package-private methods.
 */
public class ProjectTest {

    /**
     * Verifies the constructor stores every field when valid data is supplied.
     */
    @Test
    void testSuccessfulCreation() {
        Project project = new Project(
                "1",
                "Project Alpha",
                "A test project",
                ProjectStatus.ACTIVE
        );

        // Check that each field has the expected value for the project object
        assertThat(project)
                .hasFieldOrPropertyWithValue("projectId", "1")
                .hasFieldOrPropertyWithValue("name", "Project Alpha")
                .hasFieldOrPropertyWithValue("description", "A test project")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.ACTIVE);
    }

    /**
     * Ensures setters accept valid data and mutate the stored state accordingly.
     */
    @Test
    void testValidSetters() {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);
        project.setName("Project Beta");
        project.setDescription("Updated description");
        project.setStatus(ProjectStatus.ON_HOLD);

        // Check that each field has the updated value for the project object
        assertThat(project)
                .hasFieldOrPropertyWithValue("name", "Project Beta")
                .hasFieldOrPropertyWithValue("description", "Updated description")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.ON_HOLD);
    }

    /**
     * Confirms the constructor trims text inputs before persisting them.
     */
    @Test
    void testConstructorTrimsStoredValues() {
        Project project = new Project(
                " 100 ",
                "  Project Alpha ",
                "  A test project  ",
                ProjectStatus.ACTIVE
        );

        assertThat(project.getProjectId()).isEqualTo("100");
        assertThat(project.getName()).isEqualTo("Project Alpha");
        assertThat(project.getDescription()).isEqualTo("A test project");
    }

    /**
     * Verifies that empty description is allowed (min length = 0).
     */
    @Test
    void testEmptyDescriptionIsValid() {
        Project project = new Project("1", "Project Alpha", "", ProjectStatus.ACTIVE);
        assertThat(project.getDescription()).isEmpty();
    }

    /**
     * Verifies that whitespace-only description becomes empty after trimming.
     */
    @Test
    void testWhitespaceDescriptionBecomesEmpty() {
        Project project = new Project("1", "Project Alpha", "   ", ProjectStatus.ACTIVE);
        assertThat(project.getDescription()).isEmpty();
    }

    /**
     * Enumerates invalid constructor inputs (id, name, description, status).
     */
    @CsvSource(
            value = {
                    // projectId validation
                    "' ', 'Project Alpha', 'A test project', ACTIVE, 'Project ID must not be null or blank'"
                    , "'', 'Project Alpha', 'A test project', ACTIVE, 'Project ID must not be null or blank'"
                    , "null, 'Project Alpha', 'A test project', ACTIVE, 'Project ID must not be null or blank'"
                    , "12345678901, 'Project Alpha', 'A test project', ACTIVE, 'Project ID length must be between 1 and 10'"

                    // name validation
                    , "1, ' ', 'A test project', ACTIVE, 'name must not be null or blank'"
                    , "1, '', 'A test project', ACTIVE, 'name must not be null or blank'"
                    , "1, null, 'A test project', ACTIVE, 'name must not be null or blank'"
                    , "1, 'This is a very long project name that exceeds fifty characters', 'A test project', ACTIVE, 'name length must be between 1 and 50'"

                    // description validation
                    , "1, 'Project Alpha', null, ACTIVE, 'description must not be null or blank'"
                    , "1, 'Project Alpha', 'This is a very long description that exceeds one hundred characters and should be rejected by validation logic', ACTIVE, 'description length must be between 0 and 100'"

                    // status validation
                    , "1, 'Project Alpha', 'A test project', null, 'status must not be null'"
            },

            // Specify that the string "null" should be treated as a null value
            nullValues = "null"
    )
    /**
     * Verifies the constructor rejects each invalid input combination defined above.
     */
    @ParameterizedTest
    void testFailedCreation(
            String projectId,
            String name,
            String description,
            String statusStr,
            String expectedMessage // expected exception message
    ) {
        ProjectStatus status = statusStr == null ? null : ProjectStatus.valueOf(statusStr);

        // Check that creating a Project with invalid inputs throws an exception with the expected message
        assertThatThrownBy(() -> new Project(projectId, name, description, status))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Drives invalid name values for the setter validation test.
     */
    @CsvSource(
            value = {
                    "' ', 'name must not be null or blank'"
                    , "'', 'name must not be null or blank'"
                    , "null, 'name must not be null or blank'"
                    , "'This is a very long project name that exceeds fifty characters', 'name length must be between 1 and 50'"
            },
            nullValues = "null"
    )

    /**
     * Ensures {@link Project#setName(String)} throws for blank/null/too-long names.
     */
    @ParameterizedTest
    void testFailedSetName(String invalidName, String expectedMessage) {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);

        // Check that invalid name updates throw the proper exception message
        assertThatThrownBy(() -> project.setName(invalidName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Drives invalid description values for the setter validation test.
     */
    @CsvSource(
            value = {
                    "null, 'description must not be null or blank'"
                    , "'This is a very long description that exceeds one hundred characters and should be rejected by validation logic', 'description length must be between 0 and 100'"
            },
            nullValues = "null"
    )

    /**
     * Ensures {@link Project#setDescription(String)} throws for null/too-long descriptions.
     */
    @ParameterizedTest
    void testFailedSetDescription(String invalidDescription, String expectedMessage) {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);

        // Check that invalid description updates throw the proper exception message
        assertThatThrownBy(() -> project.setDescription(invalidDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Ensures {@link Project#setStatus(ProjectStatus)} throws for null status.
     */
    @Test
    void testFailedSetStatus() {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);

        // Check that null status update throws the proper exception message
        assertThatThrownBy(() -> project.setStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must not be null");
    }

    /**
     * Supplies invalid arguments to {@link #testUpdateRejectsInvalidValuesAtomically}.
     */
    private static Stream<Arguments> invalidUpdateValues() {
        return Stream.of(
                Arguments.of(" ", "A test project", ProjectStatus.ACTIVE, "name must not be null or blank"),
                Arguments.of("Project Alpha", "This is a very long description that exceeds one hundred characters and should be rejected by validation logic", ProjectStatus.ACTIVE, "description length must be between 0 and 100"),
                Arguments.of("Project Alpha", "A test project", null, "status must not be null")
        );
    }

    /**
     * Ensures {@link Project#update(String, String, ProjectStatus)} rejects invalid data
     * and leaves state unchanged (atomic update behavior).
     */
    @ParameterizedTest
    @MethodSource("invalidUpdateValues")
    void testUpdateRejectsInvalidValuesAtomically(
            String newName,
            String newDescription,
            ProjectStatus newStatus,
            String expectedMessage) {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);

        assertThatThrownBy(() -> project.update(newName, newDescription, newStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        assertThat(project)
                .hasFieldOrPropertyWithValue("name", "Project Alpha")
                .hasFieldOrPropertyWithValue("description", "A test project")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.ACTIVE);
    }

    /**
     * Ensures the copy guard rejects corrupted state (null internal fields).
     *
     * <p>Added to kill PITest mutant: "removed call to validateCopySource" in Project.copy().
     * This test uses reflection to corrupt each internal field and verify copy() throws.
     * Parameterized to achieve full branch coverage of the validateCopySource null checks.
     */
    @ParameterizedTest(name = "copy rejects null {0}")
    @MethodSource("nullFieldProvider")
    void testCopyRejectsNullInternalState(String fieldName) throws Exception {
        Project project = new Project("901", "Project Alpha", "A test project", ProjectStatus.ACTIVE);

        // Use reflection to corrupt internal state (simulate memory corruption or serialization bugs)
        Field field = Project.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(project, null);

        assertThatThrownBy(project::copy)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project copy source must not be null");
    }

    /**
     * Provides field names for the null internal state test.
     */
    static Stream<String> nullFieldProvider() {
        return Stream.of("projectId", "name", "description", "status");
    }

    /**
     * Verifies that copy() creates an independent instance with the same values.
     */
    @Test
    void testCopyCreatesIndependentInstance() {
        Project original = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);
        Project copy = original.copy();

        // Verify copy has same values
        assertThat(copy)
                .hasFieldOrPropertyWithValue("projectId", "1")
                .hasFieldOrPropertyWithValue("name", "Project Alpha")
                .hasFieldOrPropertyWithValue("description", "A test project")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.ACTIVE);

        // Verify copy is independent (modifying copy doesn't affect original)
        copy.setName("Project Beta");
        assertThat(original.getName()).isEqualTo("Project Alpha");
        assertThat(copy.getName()).isEqualTo("Project Beta");
    }

    /**
     * Verifies that update() accepts all valid values and mutates state.
     */
    @Test
    void testUpdateWithValidValues() {
        Project project = new Project("1", "Project Alpha", "A test project", ProjectStatus.ACTIVE);
        project.update("Project Beta", "Updated description", ProjectStatus.COMPLETED);

        assertThat(project)
                .hasFieldOrPropertyWithValue("name", "Project Beta")
                .hasFieldOrPropertyWithValue("description", "Updated description")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.COMPLETED);
    }

    // ==================== Additional Boundary and Edge Case Tests ====================

    /**
     * Tests project ID at exact maximum length boundary (10 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a project ID with exactly 10 characters
     * is accepted. Catches mutants that change {@code >} to {@code >=} in length validation.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code >} to {@code >=}</li>
     *   <li>Replaced MAX_ID_LENGTH constant</li>
     * </ul>
     */
    @Test
    void constructor_acceptsProjectIdAtMaximumLength() {
        final Project project = new Project("1234567890", "Project Name", "Description", ProjectStatus.ACTIVE);

        assertThat(project.getProjectId()).isEqualTo("1234567890");
        assertThat(project.getProjectId()).hasSize(10);
    }

    /**
     * Tests project ID one character over maximum length (11 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a project ID with 11 characters
     * is rejected. Tests the boundary from the rejection side.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Removed length validation</li>
     * </ul>
     */
    @Test
    void constructor_rejectsProjectIdOneOverMaximumLength() {
        assertThatThrownBy(() ->
                new Project("12345678901", "Project Name", "Description", ProjectStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project ID length must be between 1 and 10");
    }

    /**
     * Tests project name at exact maximum length boundary (50 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a project name with exactly 50 characters
     * is accepted. Tests the upper boundary of name validation.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in validateLength</li>
     *   <li>Replaced MAX_PROJECT_NAME_LENGTH constant</li>
     * </ul>
     */
    @Test
    void constructor_acceptsNameAtMaximumLength() {
        final String maxName = "12345678901234567890123456789012345678901234567890"; // 50 chars
        final Project project = new Project("1", maxName, "Description", ProjectStatus.ACTIVE);

        assertThat(project.getName()).isEqualTo(maxName);
        assertThat(project.getName()).hasSize(50);
    }

    /**
     * Tests project name one character over maximum length (51 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a project name with 51 characters
     * is rejected. Tests the boundary from the other side.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Removed validation check</li>
     * </ul>
     */
    @Test
    void constructor_rejectsNameOneOverMaximumLength() {
        final String tooLongName = "123456789012345678901234567890123456789012345678901"; // 51 chars

        assertThatThrownBy(() ->
                new Project("1", tooLongName, "Description", ProjectStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name length must be between 1 and 50");
    }

    /**
     * Tests project description at exact maximum length boundary (100 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a description with exactly 100 characters
     * is accepted. Tests the upper boundary of description validation.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code >} to {@code >=}</li>
     *   <li>Replaced MAX_PROJECT_DESCRIPTION_LENGTH constant</li>
     * </ul>
     */
    @Test
    void constructor_acceptsDescriptionAtMaximumLength() {
        final String maxDesc = "1234567890".repeat(10); // 100 chars
        final Project project = new Project("1", "Project Name", maxDesc, ProjectStatus.ACTIVE);

        assertThat(project.getDescription()).isEqualTo(maxDesc);
        assertThat(project.getDescription()).hasSize(100);
    }

    /**
     * Tests project description one character over maximum length (101 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a description with 101 characters
     * is rejected. Tests the boundary from the other side.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Removed validation check</li>
     * </ul>
     */
    @Test
    void constructor_rejectsDescriptionOneOverMaximumLength() {
        final String tooLongDesc = "1234567890".repeat(10) + "1"; // 101 chars

        assertThatThrownBy(() ->
                new Project("1", "Project Name", tooLongDesc, ProjectStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("description length must be between 0 and 100");
    }

    /**
     * Tests that description can be exactly zero characters (empty string after trimming).
     *
     * <p><b>Why this test exists:</b> Project description has minLength = 0, which is
     * different from other domain objects. This tests the special case where empty
     * is allowed. Catches mutants that change {@code minLength == 0} check.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code minLength == 0} to {@code minLength != 0}</li>
     *   <li>Removed special case handling for empty descriptions</li>
     * </ul>
     */
    @Test
    void constructor_acceptsEmptyDescriptionAfterTrimming() {
        final Project project = new Project("1", "Project Name", "", ProjectStatus.ACTIVE);

        assertThat(project.getDescription()).isEmpty();
        assertThat(project.getDescription()).hasSize(0);
    }

    /**
     * Tests setName with a name at exact maximum length.
     *
     * <p><b>Why this test exists:</b> Ensures setName correctly validates and accepts
     * a 50-character name through the setter path.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in setter</li>
     * </ul>
     */
    @Test
    void setName_acceptsNameAtMaximumLength() {
        final Project project = new Project("1", "Initial", "Description", ProjectStatus.ACTIVE);
        final String maxName = "12345678901234567890123456789012345678901234567890"; // 50 chars

        project.setName(maxName);

        assertThat(project.getName()).isEqualTo(maxName);
        assertThat(project.getName()).hasSize(50);
    }

    /**
     * Tests setName with a single character (minimum length).
     *
     * <p><b>Why this test exists:</b> Ensures setName accepts the minimum valid
     * length (1 character). Tests the lower boundary.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code <} to {@code <=}</li>
     * </ul>
     */
    @Test
    void setName_acceptsMinimumLengthName() {
        final Project project = new Project("1", "Initial", "Description", ProjectStatus.ACTIVE);

        project.setName("A");

        assertThat(project.getName()).isEqualTo("A");
        assertThat(project.getName()).hasSize(1);
    }

    /**
     * Tests setDescription with a description at exact maximum length.
     *
     * <p><b>Why this test exists:</b> Ensures setDescription correctly validates
     * and accepts a 100-character description.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in setter</li>
     * </ul>
     */
    @Test
    void setDescription_acceptsDescriptionAtMaximumLength() {
        final Project project = new Project("1", "Project", "Initial", ProjectStatus.ACTIVE);
        final String maxDesc = "1234567890".repeat(10); // 100 chars

        project.setDescription(maxDesc);

        assertThat(project.getDescription()).isEqualTo(maxDesc);
        assertThat(project.getDescription()).hasSize(100);
    }

    /**
     * Tests setDescription with empty string (minimum length = 0).
     *
     * <p><b>Why this test exists:</b> Ensures setDescription accepts empty string
     * since description has minLength = 0. Tests the special lower boundary.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional check for empty description</li>
     *   <li>Removed special case for minLength == 0</li>
     * </ul>
     */
    @Test
    void setDescription_acceptsEmptyDescription() {
        final Project project = new Project("1", "Project", "Initial", ProjectStatus.ACTIVE);

        project.setDescription("");

        assertThat(project.getDescription()).isEmpty();
        assertThat(project.getDescription()).hasSize(0);
    }

    /**
     * Tests update() with maximum length values for name and description.
     *
     * <p><b>Why this test exists:</b> Ensures atomic update works correctly when
     * both fields are at their maximum length boundaries.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in update validation</li>
     *   <li>Swapped field assignments</li>
     * </ul>
     */
    @Test
    void update_acceptsMaximumLengthValues() {
        final Project project = new Project("1", "Old", "Old desc", ProjectStatus.ACTIVE);
        final String maxName = "12345678901234567890123456789012345678901234567890"; // 50 chars
        final String maxDesc = "1234567890".repeat(10); // 100 chars

        project.update(maxName, maxDesc, ProjectStatus.COMPLETED);

        assertThat(project.getName()).isEqualTo(maxName);
        assertThat(project.getDescription()).isEqualTo(maxDesc);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    }

    /**
     * Tests update() with empty description.
     *
     * <p><b>Why this test exists:</b> Ensures update() correctly handles the special
     * case of empty description (minLength = 0).
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed empty description handling in update</li>
     * </ul>
     */
    @Test
    void update_acceptsEmptyDescription() {
        final Project project = new Project("1", "Old", "Old desc", ProjectStatus.ACTIVE);

        project.update("New Name", "", ProjectStatus.ON_HOLD);

        assertThat(project.getName()).isEqualTo("New Name");
        assertThat(project.getDescription()).isEmpty();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ON_HOLD);
    }

    /**
     * Tests that setStatus actually updates the status value.
     *
     * <p><b>Why this test exists:</b> Ensures setStatus correctly assigns the new
     * status. Catches mutants that don't assign or assign wrong value.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed assignment in setStatus</li>
     *   <li>Assigned wrong status value</li>
     * </ul>
     */
    @Test
    void setStatus_updatesStatusCorrectly() {
        final Project project = new Project("1", "Project", "Desc", ProjectStatus.ACTIVE);

        project.setStatus(ProjectStatus.ARCHIVED);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.getStatus()).isNotEqualTo(ProjectStatus.ACTIVE);
    }

    /**
     * Tests all possible ProjectStatus enum values can be set.
     *
     * <p><b>Why this test exists:</b> Ensures all enum values are valid and can be
     * set without errors. Tests that no enum values are accidentally rejected.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Added validation that rejects specific enum values</li>
     * </ul>
     */
    @Test
    void constructor_acceptsAllProjectStatusValues() {
        // Test each enum value
        assertThatNoException().isThrownBy(() ->
                new Project("1", "Project", "Desc", ProjectStatus.ACTIVE));
        assertThatNoException().isThrownBy(() ->
                new Project("2", "Project", "Desc", ProjectStatus.ON_HOLD));
        assertThatNoException().isThrownBy(() ->
                new Project("3", "Project", "Desc", ProjectStatus.COMPLETED));
        assertThatNoException().isThrownBy(() ->
                new Project("4", "Project", "Desc", ProjectStatus.ARCHIVED));
    }

    /**
     * Tests copy() with a project that has an empty description.
     *
     * <p><b>Why this test exists:</b> Ensures copy() correctly handles the special
     * case of empty description (minLength = 0).
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed empty description handling in copy</li>
     *   <li>Failed to copy empty description</li>
     * </ul>
     */
    @Test
    void copy_handlesEmptyDescriptionCorrectly() {
        final Project original = new Project("99", "Project Name", "", ProjectStatus.ACTIVE);

        final Project copy = original.copy();

        assertThat(copy.getDescription()).isEmpty();
        assertThat(copy.getDescription()).hasSize(0);
        assertThat(copy).isNotSameAs(original);
    }

    /**
     * Tests that modifying a copy doesn't affect the original project.
     *
     * <p><b>Why this test exists:</b> Ensures copy() creates a true independent copy
     * without shared mutable state.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Returned 'this' instead of new instance</li>
     * </ul>
     */
    @Test
    void copy_modificationsToopyDontAffectOriginal() {
        final Project original = new Project("99", "Project Alpha", "Description", ProjectStatus.ACTIVE);
        final Project copy = original.copy();

        // Modify the copy
        copy.setName("Project Beta");
        copy.setDescription("New Description");
        copy.setStatus(ProjectStatus.COMPLETED);

        // Verify original is unchanged
        assertThat(original.getName()).isEqualTo("Project Alpha");
        assertThat(original.getDescription()).isEqualTo("Description");
        assertThat(original.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    /**
     * Tests that validateAndTrimText correctly trims input before validation.
     *
     * <p><b>Why this test exists:</b> Ensures leading/trailing whitespace is removed
     * before length validation. A string with spaces that becomes valid after trimming
     * should be accepted.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed trim() call before validation</li>
     *   <li>Validated before trimming</li>
     * </ul>
     */
    @Test
    void constructor_trimsNameBeforeValidation() {
        final Project project = new Project("1", "  ProjectName  ", "Desc", ProjectStatus.ACTIVE);

        // Name should be trimmed
        assertThat(project.getName()).isEqualTo("ProjectName");
        assertThat(project.getName()).doesNotStartWith(" ");
        assertThat(project.getName()).doesNotEndWith(" ");
    }

    /**
     * Tests that description is trimmed even when it becomes empty.
     *
     * <p><b>Why this test exists:</b> Ensures whitespace-only description becomes
     * empty after trimming, which is valid since minLength = 0.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed trim() call for description</li>
     *   <li>Changed empty handling after trim</li>
     * </ul>
     */
    @Test
    void constructor_trimsWhitespaceDescriptionToEmpty() {
        final Project project = new Project("1", "Project", "     ", ProjectStatus.ACTIVE);

        // Whitespace-only description should become empty after trim
        assertThat(project.getDescription()).isEmpty();
    }
}
