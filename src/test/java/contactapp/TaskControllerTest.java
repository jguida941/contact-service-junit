package contactapp;

import contactapp.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.stream.Stream;

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
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @BeforeEach
    void setUp() throws Exception {
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

        mockMvc.perform(delete("/api/v1/tasks/105"))
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
        mockMvc.perform(delete("/api/v1/tasks/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createTask_duplicateId_returns409() throws Exception {
        createTestTask("106", "First Task", "First task description");

        mockMvc.perform(post("/api/v1/tasks")
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
    void createTask_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidTaskInputs() {
        return Stream.of(
                // ID validation
                Arguments.of("id (blank)", """
                    {"id": "", "name": "Test", "description": "Test description"}
                    """, "id"),
                Arguments.of("id (too long)", """
                    {"id": "12345678901", "name": "Test", "description": "Test description"}
                    """, "id"),

                // name validation
                Arguments.of("name (blank)", """
                    {"id": "100", "name": "", "description": "Test description"}
                    """, "name"),
                Arguments.of("name (too long)", """
                    {"id": "100", "name": "123456789012345678901", "description": "Test description"}
                    """, "name"),

                // description validation
                Arguments.of("description (blank)", """
                    {"id": "100", "name": "Test", "description": ""}
                    """, "description"),
                Arguments.of("description (too long)", """
                    {"id": "100", "name": "Test", "description": "123456789012345678901234567890123456789012345678901"}
                    """, "description")
        );
    }

    // ==================== Boundary Tests (Bean Validation accuracy) ====================

    @Test
    void createTask_exactlyMaxLengthFields_accepted() throws Exception {
        // Test exact boundaries: id=10, name=20, description=50
        mockMvc.perform(post("/api/v1/tasks")
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
                        org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    void createTask_nameOneOverMax_rejected() throws Exception {
        // name max is 20, this is 21
        mockMvc.perform(post("/api/v1/tasks")
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
                        org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void createTask_descriptionOneOverMax_rejected() throws Exception {
        // description max is 50, this is 51
        mockMvc.perform(post("/api/v1/tasks")
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
                        org.hamcrest.Matchers.containsString("description")));
    }

    // ==================== Malformed JSON Test ====================

    @Test
    void createTask_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
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
}
