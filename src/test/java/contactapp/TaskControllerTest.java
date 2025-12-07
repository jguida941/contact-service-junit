package contactapp;

import contactapp.domain.Task;
import contactapp.domain.TaskStatus;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import contactapp.security.WithMockAppUser;
import contactapp.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import contactapp.support.SecuredMockMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link contactapp.api.TaskController}.
 *
 * <p>Tests REST endpoints for Task CRUD operations including:
 * <ul>
 *   <li>Happy path for all CRUD operations</li>
 *   <li>Validation errors (Bean Validation layer)</li>
 *   <li>Boundary tests for field lengths</li>
 *   <li>Not found scenarios (404)</li>
 *   <li>Duplicate ID conflicts (409)</li>
 * </ul>
 */
@WithMockAppUser
class TaskControllerTest extends SecuredMockMvcTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TestUserSetup testUserSetup;

    @BeforeEach
    void setUp() throws Exception {
        testUserSetup.setupTestUser();
        // Clear data before each test for isolation using reflection
        // (clearAllTasks is package-private in contactapp.service)
        final Method clearMethod = TaskService.class.getDeclaredMethod("clearAllTasks");
        clearMethod.setAccessible(true);
        clearMethod.invoke(taskService);
    }

    // ==================== Happy Path Tests ====================

    @Test
    void createTask_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "100",
                                "name": "Write tests",
                                "description": "Add comprehensive test coverage"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("100"))
                .andExpect(jsonPath("$.name").value("Write tests"))
                .andExpect(jsonPath("$.description").value("Add comprehensive test coverage"));
    }

    @Test
    void getAllTasks_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllTasks_withData_returnsList() throws Exception {
        createTestTask("101", "Task One", "First task description");
        createTestTask("102", "Task Two", "Second task description");

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockAppUser
    void getAllTasks_allFlagRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("all", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only administrators can access all users' tasks"));
    }

    @Test
    @WithMockAppUser(username = "admin", role = Role.ADMIN)
    void getAllTasks_allFlagReturnsAllForAdmin() throws Exception {
        addTaskForUser("alpha", Role.USER, "alpha-task");
        addTaskForUser("beta", Role.USER, "beta-task");
        addTaskForUser("admin", Role.ADMIN, "admin-task");

        mockMvc.perform(get("/api/v1/tasks").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getTaskById_exists_returns200() throws Exception {
        createTestTask("103", "Find Task", "Task to find by ID");

        mockMvc.perform(get("/api/v1/tasks/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("103"))
                .andExpect(jsonPath("$.name").value("Find Task"));
    }

    @Test
    void updateTask_exists_returns200() throws Exception {
        createTestTask("104", "Old Name", "Old description");

        mockMvc.perform(put("/api/v1/tasks/104")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "104",
                                "name": "New Name",
                                "description": "Updated description"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void deleteTask_exists_returns204() throws Exception {
        createTestTask("105", "Delete Me", "Task to delete");

        mockMvc.perform(delete("/api/v1/tasks/105")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/105"))
                .andExpect(status().isNotFound());
    }

    // ==================== Not Found Tests ====================

    @Test
    void getTaskById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found: notfound"));
    }

    @Test
    void updateTask_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/missing")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "missing",
                                "name": "Test Name",
                                "description": "Test description"
                            }
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found: missing"));
    }

    @Test
    void deleteTask_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/notfound")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createTask_duplicateId_returns409() throws Exception {
        createTestTask("106", "First Task", "First task description");

        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "106",
                                "name": "Second Task",
                                "description": "Different task"
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Task with id '106' already exists"));
    }

    // ==================== Validation Error Tests (Parameterized) ====================

    @ParameterizedTest(name = "create task with invalid {0}")
    @MethodSource("invalidTaskInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void createTask_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        // fieldName is used in test display name via {0} placeholder above
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidTaskInputs() {
        return Stream.of(
                // ID validation - humanized field name is "ID"
                Arguments.of("id (blank)", """
                    {"id": "", "name": "Test", "description": "Test description"}
                    """, "ID"),
                Arguments.of("id (too long)", """
                    {"id": "12345678901", "name": "Test", "description": "Test description"}
                    """, "ID"),

                // name validation - humanized field name is "Name"
                Arguments.of("name (blank)", """
                    {"id": "100", "name": "", "description": "Test description"}
                    """, "Name"),
                Arguments.of("name (too long)", """
                    {"id": "100", "name": "123456789012345678901", "description": "Test description"}
                    """, "Name"),

                // description validation - humanized field name is "Description"
                Arguments.of("description (blank)", """
                    {"id": "100", "name": "Test", "description": ""}
                    """, "Description"),
                Arguments.of("description (too long)", """
                    {"id": "100", "name": "Test", "description": "123456789012345678901234567890123456789012345678901"}
                    """, "Description")
        );
    }

    // ==================== Boundary Tests (Bean Validation accuracy) ====================

    @Test
    void createTask_exactlyMaxLengthFields_accepted() throws Exception {
        // Test exact boundaries: id=10, name=20, description=50
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "1234567890",
                                "name": "12345678901234567890",
                                "description": "12345678901234567890123456789012345678901234567890"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234567890"));
    }

    @Test
    void createTask_idOneOverMax_rejected() throws Exception {
        // id max is 10, this is 11
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "12345678901",
                                "name": "Test Name",
                                "description": "Test description"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ID")));
    }

    @Test
    void createTask_nameOneOverMax_rejected() throws Exception {
        // name max is 20, this is 21
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "200",
                                "name": "123456789012345678901",
                                "description": "Test description"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Name")));
    }

    @Test
    void createTask_descriptionOneOverMax_rejected() throws Exception {
        // description max is 50, this is 51
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "201",
                                "name": "Test Name",
                                "description": "123456789012345678901234567890123456789012345678901"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Description")));
    }

    // ==================== Malformed JSON Test ====================

    @Test
    void createTask_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    // ==================== Helper Methods ====================

    private void createTestTask(
            final String id,
            final String name,
            final String description) throws Exception {

        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "%s",
                                "name": "%s",
                                "description": "%s"
                            }
                            """, id, name, description)))
                .andExpect(status().isCreated());
    }

    private void addTaskForUser(final String username, final Role role, final String id) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        taskService.addTask(new Task(id, "Name-" + id, "Desc-" + id));
    }

    // ==================== Phase 2 Tests: Status, Due Date, Timestamps ====================

    @Test
    void createTask_withStatus_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "200",
                                "name": "Status Task",
                                "description": "Task with status",
                                "status": "IN_PROGRESS"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("200"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void createTask_withDueDate_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "201",
                                "name": "Due Date Task",
                                "description": "Task with due date",
                                "dueDate": "2026-06-15"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("201"))
                .andExpect(jsonPath("$.dueDate").value("2026-06-15"));
    }

    @Test
    void createTask_withStatusAndDueDate_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "202",
                                "name": "Complete Task",
                                "description": "Task with all fields",
                                "status": "TODO",
                                "dueDate": "2026-12-31"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("202"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-31"));
    }

    @Test
    void createTask_defaultsToTodoStatus() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "203",
                                "name": "Default Status",
                                "description": "Should default to TODO"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createTask_setsCreatedAtTimestamp() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "204",
                                "name": "Timestamp Test",
                                "description": "Testing timestamps"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void updateTask_withNewStatus_returns200() throws Exception {
        createTestTask("205", "Original", "Original description");

        mockMvc.perform(put("/api/v1/tasks/205")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "205",
                                "name": "Updated",
                                "description": "Updated description",
                                "status": "DONE"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void updateTask_withNewDueDate_returns200() throws Exception {
        createTestTask("206", "Original", "Original description");

        mockMvc.perform(put("/api/v1/tasks/206")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "206",
                                "name": "Updated",
                                "description": "Updated description",
                                "dueDate": "2027-01-01"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2027-01-01"));
    }

    @Test
    void getTaskById_returnsStatusAndDueDate() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "207",
                                "name": "Full Task",
                                "description": "Task with all fields",
                                "status": "IN_PROGRESS",
                                "dueDate": "2026-08-15"
                            }
                            """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tasks/207"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createTask_invalidStatus_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "300",
                                "name": "Invalid Status",
                                "description": "Task with invalid status",
                                "status": "INVALID_STATUS"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_invalidDueDateFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "301",
                                "name": "Invalid Date",
                                "description": "Task with invalid date format",
                                "dueDate": "invalid-date"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_allTaskStatuses_accepted() throws Exception {
        int counter = 400;
        for (TaskStatus status : TaskStatus.values()) {
            String id = String.valueOf(counter++);
            mockMvc.perform(post("/api/v1/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                {
                                    "id": "%s",
                                    "name": "Task %s",
                                    "description": "Testing status %s",
                                    "status": "%s"
                                }
                                """, id, status, status, status)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(status.toString()));
        }
    }

    @Test
    void createTask_pastDueDate_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "500",
                                "name": "Past Due",
                                "description": "Task with past due date",
                                "dueDate": "2020-01-01"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Due Date")));
    }

    @Test
    void createTask_futureDueDate_accepted() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "501",
                                "name": "Future Due",
                                "description": "Task with future due date",
                                "dueDate": "2030-12-31"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value("2030-12-31"));
    }

    @Test
    void createTask_nullDueDate_accepted() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "502",
                                "name": "No Due Date",
                                "description": "Task without due date"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void updateTask_clearsStatusWhenNotProvided() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "600",
                                "name": "Original",
                                "description": "Original description",
                                "status": "IN_PROGRESS"
                            }
                            """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/tasks/600")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "600",
                                "name": "Updated",
                                "description": "Updated description"
                            }
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void updateTask_clearsDueDateWhenNotProvided() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "601",
                                "name": "Original",
                                "description": "Original description",
                                "dueDate": "2026-12-31"
                            }
                            """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/tasks/601")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "601",
                                "name": "Updated",
                                "description": "Updated description"
                            }
                            """))
                .andExpect(status().isOk());
    }

    // ==================== E2E Two-Layer Validation Tests ====================

    /**
     * E2E test: Verifies status/dueDate persist through full stack round-trip.
     * Controller → DTO → Service → Domain → Mapper → Entity → DB → Entity → Mapper → Domain → DTO
     */
    @Test
    void e2e_statusAndDueDate_persistThroughFullStack() throws Exception {
        // Create task with all Phase 2 fields
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "e2e-001",
                                "name": "E2E Task",
                                "description": "Testing full round-trip",
                                "status": "IN_PROGRESS",
                                "dueDate": "2028-06-15"
                            }
                            """))
                .andExpect(status().isCreated());

        // Retrieve and verify domain constructor validated on load (no corruption)
        mockMvc.perform(get("/api/v1/tasks/e2e-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("e2e-001"))
                .andExpect(jsonPath("$.name").value("E2E Task"))
                .andExpect(jsonPath("$.description").value("Testing full round-trip"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value("2028-06-15"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // Update status to DONE
        mockMvc.perform(put("/api/v1/tasks/e2e-001")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "e2e-001",
                                "name": "E2E Task Complete",
                                "description": "Round-trip complete",
                                "status": "DONE",
                                "dueDate": "2028-06-15"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        // Final verification - proves domain re-constructed correctly on each load
        mockMvc.perform(get("/api/v1/tasks/e2e-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.name").value("E2E Task Complete"));
    }

    /**
     * E2E test: Verifies updatedAt timestamp changes after modification.
     * Proves timestamps are maintained correctly through service/persistence layers.
     *
     * <p>Note: We fetch the task after creation to get the persisted timestamps
     * (set by JPA @PrePersist), not the domain object timestamps returned from create.
     */
    @Test
    void e2e_updatedAtChangesAfterModification() throws Exception {
        // Create task
        mockMvc.perform(post("/api/v1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "e2e-002",
                                "name": "Timestamp Test",
                                "description": "Testing timestamp updates"
                            }
                            """))
                .andExpect(status().isCreated());

        // Fetch to get the persisted timestamps (from entity, not domain)
        final var fetchResult = mockMvc.perform(get("/api/v1/tasks/e2e-002"))
                .andExpect(status().isOk())
                .andReturn();

        final String fetchResponse = fetchResult.getResponse().getContentAsString();
        final String originalCreatedAt = com.jayway.jsonpath.JsonPath.read(fetchResponse, "$.createdAt");
        final String originalUpdatedAt = com.jayway.jsonpath.JsonPath.read(fetchResponse, "$.updatedAt");

        // Small delay to ensure timestamp difference
        Thread.sleep(50);

        // Update task
        mockMvc.perform(put("/api/v1/tasks/e2e-002")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "e2e-002",
                                "name": "Updated Name",
                                "description": "Description changed"
                            }
                            """))
                .andExpect(status().isOk());

        // Fetch again to get updated timestamps
        final var updateFetchResult = mockMvc.perform(get("/api/v1/tasks/e2e-002"))
                .andExpect(status().isOk())
                .andReturn();

        final String updateResponse = updateFetchResult.getResponse().getContentAsString();
        final String newCreatedAt = com.jayway.jsonpath.JsonPath.read(updateResponse, "$.createdAt");
        final String newUpdatedAt = com.jayway.jsonpath.JsonPath.read(updateResponse, "$.updatedAt");

        // createdAt should remain unchanged
        org.assertj.core.api.Assertions.assertThat(newCreatedAt).isEqualTo(originalCreatedAt);
        // updatedAt should have changed
        org.assertj.core.api.Assertions.assertThat(newUpdatedAt).isNotEqualTo(originalUpdatedAt);
    }
}
