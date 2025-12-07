package contactapp;

import contactapp.config.RateLimitingFilter;
import contactapp.domain.Contact;
import contactapp.domain.Project;
import contactapp.domain.ProjectStatus;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import contactapp.security.WithMockAppUser;
import contactapp.service.ContactService;
import contactapp.service.ProjectService;
import contactapp.support.SecuredMockMvcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link contactapp.api.ProjectController}.
 *
 * <p>Tests REST endpoints for Project CRUD operations including:
 * <ul>
 *   <li>Happy path for all CRUD operations</li>
 *   <li>Validation errors (Bean Validation layer)</li>
 *   <li>Not found scenarios (404)</li>
 *   <li>Duplicate ID conflicts (409)</li>
 *   <li>Status filtering</li>
 * </ul>
 *
 * @see contactapp.api.ProjectController
 */
@WithMockAppUser
class ProjectControllerTest extends SecuredMockMvcTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() throws Exception {
        // Set up test user (persisted to DB and in SecurityContext)
        testUserSetup.setupTestUser();
        // Clear rate limit buckets between tests to prevent 429 collisions
        rateLimitingFilter.clearBuckets();

        // Clear data before each test for isolation using reflection
        // (clearAllProjects is package-private in contactapp.service)
        final Method clearProjectsMethod = ProjectService.class.getDeclaredMethod("clearAllProjects");
        clearProjectsMethod.setAccessible(true);
        clearProjectsMethod.invoke(projectService);

        final Method clearContactsMethod = ContactService.class.getDeclaredMethod("clearAllContacts");
        clearContactsMethod.setAccessible(true);
        clearContactsMethod.invoke(contactService);
    }

    // ==================== Happy Path Tests ====================

    @Test
    void createProject_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "100",
                                "name": "Mobile App",
                                "description": "Customer mobile application",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("100"))
                .andExpect(jsonPath("$.name").value("Mobile App"))
                .andExpect(jsonPath("$.description").value("Customer mobile application"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAllProjects_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllProjects_withData_returnsList() throws Exception {
        // Create two projects
        createTestProject("101", "Project Alpha", "First project", ProjectStatus.ACTIVE);
        createTestProject("102", "Project Beta", "Second project", ProjectStatus.ON_HOLD);

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllProjects_withStatusFilter_returnsFiltered() throws Exception {
        createTestProject("103", "Active Project", "Active one", ProjectStatus.ACTIVE);
        createTestProject("104", "On Hold Project", "On hold one", ProjectStatus.ON_HOLD);
        createTestProject("105", "Another Active", "Active two", ProjectStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/projects").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    @WithMockAppUser
    void getAllProjects_allFlagRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("all", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only ADMIN users can access all projects"));
    }

    @Test
    @WithMockAppUser(username = "admin", role = Role.ADMIN)
    void getAllProjects_allFlagReturnsAllForAdmin() throws Exception {
        addProjectForUser("alpha", Role.USER, "alpha-1", ProjectStatus.ACTIVE);
        addProjectForUser("beta", Role.USER, "beta-1", ProjectStatus.ACTIVE);
        addProjectForUser("admin", Role.ADMIN, "admin-1", ProjectStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/projects").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockAppUser(username = "admin", role = Role.ADMIN)
    void getAllProjects_allFlagWithStatusFilter_returnsFilteredAll() throws Exception {
        addProjectForUser("alpha", Role.USER, "alpha-1", ProjectStatus.ACTIVE);
        addProjectForUser("beta", Role.USER, "beta-1", ProjectStatus.ON_HOLD);
        addProjectForUser("admin", Role.ADMIN, "admin-1", ProjectStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/projects")
                        .param("all", "true")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void getProjectById_exists_returns200() throws Exception {
        createTestProject("106", "Test Project", "Test description", ProjectStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/projects/106"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("106"))
                .andExpect(jsonPath("$.name").value("Test Project"));
    }

    @Test
    void updateProject_exists_returns200() throws Exception {
        createTestProject("107", "Old Name", "Old description", ProjectStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/projects/107")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "107",
                                "name": "New Name",
                                "description": "New description",
                                "status": "COMPLETED"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void deleteProject_exists_returns204() throws Exception {
        createTestProject("108", "To Delete", "Will be deleted", ProjectStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/projects/108")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify deletion
        mockMvc.perform(get("/api/v1/projects/108"))
                .andExpect(status().isNotFound());
    }

    // ==================== Not Found Tests ====================

    @Test
    void getProjectById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: notfound"));
    }

    @Test
    void updateProject_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/projects/missing")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "missing",
                                "name": "Test Project",
                                "description": "Test description",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: missing"));
    }

    @Test
    void deleteProject_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/notfound")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createProject_duplicateId_returns409() throws Exception {
        createTestProject("109", "First Project", "First description", ProjectStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "109",
                                "name": "Different Project",
                                "description": "Different description",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Project with id '109' already exists"));
    }

    // ==================== Validation Error Tests (Parameterized) ====================

    @ParameterizedTest(name = "create project with invalid {0}")
    @MethodSource("invalidProjectInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void createProject_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        // fieldName is used in test display name via {0} placeholder above
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidProjectInputs() {
        return Stream.of(
                // ID validation - humanized field name is "ID"
                Arguments.of("id (blank)", """
                    {"id": "", "name": "Test", "description": "Desc", "status": "ACTIVE"}
                    """, "ID"),
                Arguments.of("id (too long)", """
                    {"id": "12345678901", "name": "Test", "description": "Desc", "status": "ACTIVE"}
                    """, "ID"),

                // name validation - humanized field name is "Name"
                Arguments.of("name (blank)", """
                    {"id": "100", "name": "", "description": "Desc", "status": "ACTIVE"}
                    """, "Name"),
                Arguments.of("name (too long)", """
                    {"id": "100", "name": "123456789012345678901234567890123456789012345678901", "description": "Desc", "status": "ACTIVE"}
                    """, "Name"),

                // description validation (max length) - humanized field name is "Description"
                Arguments.of("description (too long)", """
                    {"id": "100", "name": "Test", "description": "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901", "status": "ACTIVE"}
                    """, "Description"),

                // status validation - humanized field name is "Status"
                Arguments.of("status (null)", """
                    {"id": "100", "name": "Test", "description": "Desc", "status": null}
                    """, "Status"),
                // Invalid enum values fail at Jackson deserialization, not Bean Validation
                Arguments.of("status (invalid)", """
                    {"id": "100", "name": "Test", "description": "Desc", "status": "INVALID_STATUS"}
                    """, "JSON")
        );
    }

    // ==================== Malformed JSON Test ====================

    @Test
    void createProject_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    // ==================== Boundary Tests (Bean Validation accuracy) ====================

    @Test
    void createProject_exactlyMaxLengthFields_accepted() throws Exception {
        // Test exact boundaries: id=10, name=50, description=100
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "1234567890",
                                "name": "12345678901234567890123456789012345678901234567890",
                                "description": "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234567890"));
    }

    @Test
    void createProject_idOneOverMax_rejected() throws Exception {
        // id max is 10, this is 11
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "12345678901",
                                "name": "Test",
                                "description": "Test description",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ID")));
    }

    @Test
    void createProject_nameOneOverMax_rejected() throws Exception {
        // name max is 50, this is 51
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "200",
                                "name": "123456789012345678901234567890123456789012345678901",
                                "description": "Test description",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Name")));
    }

    @Test
    void createProject_descriptionOneOverMax_rejected() throws Exception {
        // description max is 100, this is 101
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "201",
                                "name": "Test",
                                "description": "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Description")));
    }

    // ==================== Edge Case Tests ====================

    @Test
    void getProjectById_trimmedId_findsProject() throws Exception {
        createTestProject("110", "Trimmed Test", "Test description", ProjectStatus.ACTIVE);

        // The controller trims the ID before lookup, so "110" should match
        mockMvc.perform(get("/api/v1/projects/110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("110"));
    }

    @Test
    void createProject_trimmedFields_storesCorrectly() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": " 111 ",
                                "name": " Trimmed Name ",
                                "description": " Trimmed Description ",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("111"))
                .andExpect(jsonPath("$.name").value("Trimmed Name"));
    }

    @Test
    void createProject_emptyDescription_accepted() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "112",
                                "name": "No Description",
                                "description": "",
                                "status": "ACTIVE"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("112"))
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void getAllProjects_multipleStatuses_filtersCorrectly() throws Exception {
        createTestProject("113", "Active 1", "Desc 1", ProjectStatus.ACTIVE);
        createTestProject("114", "Completed 1", "Desc 2", ProjectStatus.COMPLETED);
        createTestProject("115", "Active 2", "Desc 3", ProjectStatus.ACTIVE);
        createTestProject("116", "Archived 1", "Desc 4", ProjectStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/projects").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/projects").param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }

    // ==================== Helper Methods ====================

    private void createTestProject(
            final String id,
            final String name,
            final String description,
            final ProjectStatus status) throws Exception {

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "%s",
                                "name": "%s",
                                "description": "%s",
                                "status": "%s"
                            }
                            """, id, name, description, status)))
                .andExpect(status().isCreated());
    }

    private void addProjectForUser(final String username, final Role role, final String id, final ProjectStatus status) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        projectService.addProject(new Project(id, "Project-" + id, "Description-" + id, status));
    }

    // ==================== Contact Linking Tests ====================

    @Test
    void addContactToProject_validRequest_returns204() throws Exception {
        projectService.addProject(new Project("proj1", "Test Project", "Description", ProjectStatus.ACTIVE));
        contactService.addContact(new Contact("c1", "John", "Doe", "1234567890", "123 Main St"));

        mockMvc.perform(post("/api/v1/projects/proj1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "contactId": "c1",
                                "role": "CLIENT"
                            }
                            """))
                .andExpect(status().isNoContent());
    }

    @Test
    void addContactToProject_withoutRole_returns204() throws Exception {
        projectService.addProject(new Project("proj2", "Test Project", "Description", ProjectStatus.ACTIVE));
        contactService.addContact(new Contact("c2", "Jane", "Doe", "9876543210", "456 Oak St"));

        mockMvc.perform(post("/api/v1/projects/proj2/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "contactId": "c2"
                            }
                            """))
                .andExpect(status().isNoContent());
    }

    @Test
    void addContactToProject_missingContactId_returns400() throws Exception {
        projectService.addProject(new Project("proj3", "Test Project", "Description", ProjectStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/projects/proj3/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "role": "CLIENT"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addContactToProject_projectNotFound_returns404() throws Exception {
        contactService.addContact(new Contact("c3", "Bob", "Smith", "5555555555", "789 Elm St"));

        mockMvc.perform(post("/api/v1/projects/notfound/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "contactId": "c3",
                                "role": "CLIENT"
                            }
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    void addContactToProject_contactNotFound_returns404() throws Exception {
        projectService.addProject(new Project("proj4", "Test Project", "Description", ProjectStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/projects/proj4/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "contactId": "notfound",
                                "role": "CLIENT"
                            }
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeContactFromProject_validRequest_returns204() throws Exception {
        projectService.addProject(new Project("proj5", "Test Project", "Description", ProjectStatus.ACTIVE));
        contactService.addContact(new Contact("c5", "Charlie", "Davis", "2222222222", "222 Second St"));
        projectService.addContactToProject("proj5", "c5", "VENDOR");

        mockMvc.perform(delete("/api/v1/projects/proj5/contacts/c5")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeContactFromProject_linkNotExists_returns404() throws Exception {
        projectService.addProject(new Project("proj6", "Test Project", "Description", ProjectStatus.ACTIVE));
        contactService.addContact(new Contact("c6", "Diana", "Evans", "3333333333", "333 Third St"));

        mockMvc.perform(delete("/api/v1/projects/proj6/contacts/c6")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeContactFromProject_projectNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/notfound/contacts/c7")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectContacts_returnsLinkedContacts() throws Exception {
        projectService.addProject(new Project("proj7", "Test Project", "Description", ProjectStatus.ACTIVE));
        contactService.addContact(new Contact("c7", "Eve", "Foster", "4444444444", "444 Fourth St"));
        contactService.addContact(new Contact("c8", "Frank", "Green", "5555555555", "555 Fifth St"));
        projectService.addContactToProject("proj7", "c7", "CLIENT");
        projectService.addContactToProject("proj7", "c8", "STAKEHOLDER");

        mockMvc.perform(get("/api/v1/projects/proj7/contacts")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id=='c7')].firstName").value("Eve"))
                .andExpect(jsonPath("$[?(@.id=='c8')].firstName").value("Frank"));
    }

    @Test
    void getProjectContacts_emptyWhenNoContacts() throws Exception {
        projectService.addProject(new Project("proj8", "Test Project", "Description", ProjectStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/projects/proj8/contacts")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getProjectContacts_projectNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/notfound/contacts")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
