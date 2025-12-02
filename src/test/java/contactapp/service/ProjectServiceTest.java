package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Contact;
import contactapp.domain.Project;
import contactapp.domain.ProjectStatus;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the ProjectService behavior.
 *
 * <p>Runs against the Spring context (H2 + Flyway) so the service exercises the real
 * persistence layer instead of the legacy in-memory map. Tests stay in the same package
 * to access package-private helpers like {@link ProjectService#clearAllProjects()}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ProjectServiceTest {

    @Autowired
    private ProjectService service;

    @Autowired
    private ContactService contactService;

    @Autowired
    private TestUserSetup testUserSetup;

    /**
     * Sets up test user and clears data before each test.
     */
    @BeforeEach
    void clearBeforeTest() {
        testUserSetup.setupTestUser();
        service.clearAllProjects();
    }

    /**
     * Ensures the Spring-managed bean and legacy singleton reference are the same instance.
     */
    @Test
    void testSingletonSharesStateWithSpringBean() {
        ProjectService singleton = ProjectService.getInstance();
        singleton.clearAllProjects();

        Project legacyProject = new Project(
                "legacy-100",
                "Legacy Project",
                "Legacy project description",
                ProjectStatus.ACTIVE
        );

        boolean addedViaSingleton = singleton.addProject(legacyProject);

        assertThat(addedViaSingleton).isTrue();
        assertThat(service.getProjectById("legacy-100")).isPresent();
    }

    /**
     * Confirms {@link ProjectService#addProject(Project)} inserts new projects and stores them in the map.
     */
    @Test
    void testAddProject() {
        Project project = new Project(
                "100",
                "Test Project",
                "Test project description",
                ProjectStatus.ACTIVE
        );

        boolean added = service.addProject(project);

        assertThat(added).isTrue();
        assertThat(service.getDatabase()).containsKey("100");
        Project stored = service.getDatabase().get("100");
        assertThat(stored.getName()).isEqualTo("Test Project");
        assertThat(stored.getDescription()).isEqualTo("Test project description");
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    /**
     * Proves {@link ProjectService#deleteProject(String)} removes existing projects.
     */
    @Test
    void testDeleteProject() {
        ProjectService projectService = this.service;
        Project project = new Project(
                "100",
                "Test Project",
                "Test project description",
                ProjectStatus.ACTIVE
        );

        boolean added = projectService.addProject(project);

        assertThat(added).isTrue();
        assertThat(projectService.getDatabase()).containsKey("100");

        boolean deleted = projectService.deleteProject("100");

        assertThat(deleted).isTrue();
        assertThat(projectService.getDatabase()).doesNotContainKey("100");
    }

    /**
     * Ensures delete returns {@code false} when the project ID is missing.
     */
    @Test
    void testDeleteMissingProjectReturnsFalse() {
        ProjectService projectService = this.service;
        assertThat(projectService.deleteProject("missing-id")).isFalse();
        assertThat(projectService.getDatabase()).isEmpty();
    }

    /**
     * Verifies {@link ProjectService#updateProject} updates stored projects.
     */
    @Test
    void testUpdateProject() {
        ProjectService projectService = this.service;
        Project project = new Project(
                "100",
                "Test Project",
                "Test project description",
                ProjectStatus.ACTIVE
        );

        boolean added = projectService.addProject(project);
        assertThat(added).isTrue();
        assertThat(projectService.getDatabase()).containsKey("100");

        boolean updated = projectService.updateProject(
                "100",
                "Updated Project",
                "Updated description",
                ProjectStatus.ON_HOLD
        );

        assertThat(updated).isTrue();

        assertThat(projectService.getDatabase().get("100"))
                .hasFieldOrPropertyWithValue("name", "Updated Project")
                .hasFieldOrPropertyWithValue("description", "Updated description")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.ON_HOLD);
    }

    /**
     * Confirms IDs are trimmed before lookups during updates.
     */
    @Test
    void testUpdateProjectTrimsId() {
        ProjectService service = this.service;
        Project project = new Project(
                "200",
                "Test Project",
                "Test project description",
                ProjectStatus.ACTIVE
        );

        service.addProject(project);

        boolean updated = service.updateProject(
                " 200 ",
                "Updated Project",
                "Updated description",
                ProjectStatus.COMPLETED
        );

        assertThat(updated).isTrue();
        assertThat(service.getDatabase().get("200"))
                .hasFieldOrPropertyWithValue("name", "Updated Project")
                .hasFieldOrPropertyWithValue("description", "Updated description")
                .hasFieldOrPropertyWithValue("status", ProjectStatus.COMPLETED);
    }

    /**
     * Ensures update throws when the ID is blank so validation mirrors delete().
     */
    @Test
    void testUpdateProjectBlankIdThrows() {
        ProjectService service = this.service;

        assertThatThrownBy(() -> service.updateProject(" ", "Name", "Description", ProjectStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId must not be null or blank");
    }

    /**
     * Ensures duplicate project IDs are rejected and the original remains stored.
     */
    @Test
    void testAddDuplicateProjectFails() {
        ProjectService service = this.service;
        Project project1 = new Project("100", "Project 1", "Description 1", ProjectStatus.ACTIVE);
        Project project2 = new Project("100", "Project 2", "Description 2", ProjectStatus.ACTIVE);

        assertThat(service.addProject(project1)).isTrue();
        assertThatThrownBy(() -> service.addProject(project2))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("100");
        // Verify original data is still stored
        Project stored = service.getDatabase().get("100");
        assertThat(stored.getName()).isEqualTo("Project 1");
        assertThat(stored.getDescription()).isEqualTo("Description 1");
    }

    /**
     * Validates update returns {@code false} when the project ID does not exist.
     */
    @Test
    void testUpdateMissingProjectReturnsFalse() {
        ProjectService service = this.service;

        boolean updated = service.updateProject(
                "does-not-exist",
                "Updated Project",
                "Updated description",
                ProjectStatus.ACTIVE
        );

        assertThat(updated).isFalse();
    }

    /**
     * Ensures delete throws when passed a blank ID.
     */
    @Test
    void testDeleteProjectBlankIdThrows() {
        ProjectService service = this.service;

        assertThatThrownBy(() -> service.deleteProject(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId must not be null or blank");
    }

    /**
     * Verifies {@link ProjectService#addProject(Project)} guards against null input.
     */
    @Test
    void testAddProjectNullThrows() {
        ProjectService service = this.service;

        assertThatThrownBy(() -> service.addProject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project must not be null");
    }

    /**
     * Ensures getDatabase returns defensive copies so external mutation cannot alter service state.
     */
    @Test
    void testGetDatabaseReturnsDefensiveCopies() {
        ProjectService service = this.service;
        Project project = new Project("500", "Original Project", "Original Description", ProjectStatus.ACTIVE);
        service.addProject(project);

        // Get a snapshot and mutate it
        Project snapshot = service.getDatabase().get("500");
        snapshot.setName("Mutated Project");
        snapshot.setDescription("Mutated Description");
        snapshot.setStatus(ProjectStatus.ARCHIVED);

        // Fetch a fresh snapshot and verify the service state is unchanged
        Project freshSnapshot = service.getDatabase().get("500");
        assertThat(freshSnapshot.getName()).isEqualTo("Original Project");
        assertThat(freshSnapshot.getDescription()).isEqualTo("Original Description");
        assertThat(freshSnapshot.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    // ==================== getProjectById Tests ====================

    /**
     * Verifies getProjectById returns the project when it exists.
     */
    @Test
    void testGetProjectByIdReturnsProject() {
        ProjectService service = this.service;
        Project project = new Project("600", "Test Project", "Test Description", ProjectStatus.ACTIVE);
        service.addProject(project);

        var result = service.getProjectById("600");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Project");
    }

    /**
     * Verifies getProjectById returns empty when project doesn't exist.
     */
    @Test
    void testGetProjectByIdReturnsEmptyWhenNotFound() {
        ProjectService service = this.service;

        var result = service.getProjectById("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getProjectById throws when ID is blank.
     */
    @Test
    void testGetProjectByIdBlankIdThrows() {
        ProjectService service = this.service;

        assertThatThrownBy(() -> service.getProjectById(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId must not be null or blank");
    }

    /**
     * Verifies getProjectById trims the ID before lookup.
     */
    @Test
    void testGetProjectByIdTrimsId() {
        ProjectService service = this.service;
        Project project = new Project("700", "Trimmed Test", "Test Description", ProjectStatus.ACTIVE);
        service.addProject(project);

        var result = service.getProjectById(" 700 ");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Trimmed Test");
    }

    /**
     * Verifies getProjectById returns a defensive copy.
     */
    @Test
    void testGetProjectByIdReturnsDefensiveCopy() {
        ProjectService service = this.service;
        Project project = new Project("800", "Original Project", "Original Description", ProjectStatus.ACTIVE);
        service.addProject(project);

        var result = service.getProjectById("800");
        assertThat(result).isPresent();
        result.get().setName("Mutated Project");

        var freshResult = service.getProjectById("800");
        assertThat(freshResult).isPresent();
        assertThat(freshResult.get().getName()).isEqualTo("Original Project");
    }

    // ==================== getAllProjects Tests ====================

    /**
     * Verifies getAllProjects returns empty list when no projects exist.
     */
    @Test
    void testGetAllProjectsReturnsEmptyList() {
        ProjectService service = this.service;

        var result = service.getAllProjects();

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getAllProjects returns all projects.
     */
    @Test
    void testGetAllProjectsReturnsAllProjects() {
        ProjectService service = this.service;
        service.addProject(new Project("901", "Project 1", "Description 1", ProjectStatus.ACTIVE));
        service.addProject(new Project("902", "Project 2", "Description 2", ProjectStatus.ON_HOLD));

        var result = service.getAllProjects();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllProjectsAllUsers_requiresAdminRole() {
        runAs("limited", Role.USER, () ->
                service.addProject(new Project("limited-1", "Limited Project", "Description", ProjectStatus.ACTIVE)));

        assertThatThrownBy(service::getAllProjectsAllUsers)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only ADMIN users");
    }

    @Test
    void getAllProjectsAllUsers_returnsDataForAdmins() {
        runAs("user-one", Role.USER, () ->
                service.addProject(new Project("u-1", "User One Project", "Description 1", ProjectStatus.ACTIVE)));
        runAs("user-two", Role.USER, () ->
                service.addProject(new Project("u-2", "User Two Project", "Description 2", ProjectStatus.ACTIVE)));

        runAs("admin-user", Role.ADMIN, () -> assertThat(service.getAllProjectsAllUsers())
                .extracting(Project::getProjectId)
                .contains("u-1", "u-2"));
    }

    @Test
    void getProjectById_onlyReturnsCurrentUsersRecords() {
        runAs("owner", Role.USER, () ->
                service.addProject(new Project("shared01", "Owner Project", "Description", ProjectStatus.ACTIVE)));

        runAs("other-user", Role.USER, () ->
                assertThat(service.getProjectById("shared01")).isEmpty());
    }

    @Test
    void addProject_duplicateIdsReturnFalseForSameUser() {
        runAs("dup-user", Role.USER, () -> {
            assertThat(service.addProject(new Project("dup-1", "Project 1", "Description 1", ProjectStatus.ACTIVE))).isTrue();
            assertThatThrownBy(() -> service.addProject(
                    new Project("dup-1", "Project 2", "Description 2", ProjectStatus.ACTIVE)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("dup-1");
        });
    }

    @Test
    void deleteProject_doesNotRemoveOtherUsersData() {
        runAs("owner", Role.USER, () ->
                service.addProject(new Project("live-1", "Owner Project", "Description", ProjectStatus.ACTIVE)));

        runAs("other", Role.USER, () ->
                assertThat(service.deleteProject("live-1")).isFalse());
    }

    @Test
    void updateProject_doesNotAffectOtherUserRecords() {
        runAs("owner", Role.USER, () ->
                service.addProject(new Project("stay-1", "Original Project", "Original Description", ProjectStatus.ACTIVE)));

        runAs("other", Role.USER, () ->
                assertThat(service.updateProject("stay-1", "Hacked Project", "Hacked Description", ProjectStatus.ARCHIVED))
                        .isFalse());
    }

    // ==================== getProjectsByStatus Tests ====================

    /**
     * Verifies getProjectsByStatus returns projects matching the status.
     */
    @Test
    void testGetProjectsByStatusReturnsMatchingProjects() {
        ProjectService service = this.service;
        service.addProject(new Project("p1", "Active Project 1", "Description 1", ProjectStatus.ACTIVE));
        service.addProject(new Project("p2", "Active Project 2", "Description 2", ProjectStatus.ACTIVE));
        service.addProject(new Project("p3", "Completed Project", "Description 3", ProjectStatus.COMPLETED));

        var result = service.getProjectsByStatus(ProjectStatus.ACTIVE);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Project::getProjectId).containsExactlyInAnyOrder("p1", "p2");
    }

    /**
     * Verifies getProjectsByStatus returns empty list when no projects match.
     */
    @Test
    void testGetProjectsByStatusReturnsEmptyWhenNoMatches() {
        ProjectService service = this.service;
        service.addProject(new Project("p1", "Active Project", "Description 1", ProjectStatus.ACTIVE));

        var result = service.getProjectsByStatus(ProjectStatus.ARCHIVED);

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getProjectsByStatus throws when status is null.
     */
    @Test
    void testGetProjectsByStatusNullThrows() {
        ProjectService service = this.service;

        assertThatThrownBy(() -> service.getProjectsByStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must not be null");
    }

    private void runAs(final String username, final Role role, final Runnable action) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        action.run();
    }
}
