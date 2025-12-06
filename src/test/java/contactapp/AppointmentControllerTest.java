package contactapp;

import contactapp.config.RateLimitingFilter;
import contactapp.domain.Appointment;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import contactapp.security.WithMockAppUser;
import contactapp.service.AppointmentService;
import contactapp.support.SecuredMockMvcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link contactapp.api.AppointmentController}.
 *
 * <p>Tests REST endpoints for Appointment CRUD operations including:
 * <ul>
 *   <li>Happy path for all CRUD operations</li>
 *   <li>Validation errors (Bean Validation layer)</li>
 *   <li>Boundary tests for field lengths</li>
 *   <li>Date validation (past date rejection)</li>
 *   <li>Not found scenarios (404)</li>
 *   <li>Duplicate ID conflicts (409)</li>
 * </ul>
 */
@WithMockAppUser
class AppointmentControllerTest extends SecuredMockMvcTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    /** Future date for valid appointments (1 day from now). */
    private String futureDate;

    /** Past date for invalid appointments (1 day ago). */
    private String pastDate;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        testUserSetup.setupTestUser();
        // Clear rate limit buckets between tests to prevent 429 collisions
        rateLimitingFilter.clearBuckets();
        // Clear data before each test for isolation using reflection
        // (clearAllAppointments is package-private in contactapp.service)
        final Method clearMethod = AppointmentService.class.getDeclaredMethod("clearAllAppointments");
        clearMethod.setAccessible(true);
        clearMethod.invoke(appointmentService);

        // Set up dates for tests
        final Instant now = Instant.now();
        futureDate = DATE_FORMATTER.format(now.plus(1, ChronoUnit.DAYS));
        pastDate = DATE_FORMATTER.format(now.minus(1, ChronoUnit.DAYS));
    }

    // ==================== Happy Path Tests ====================

    @Test
    void createAppointment_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "100",
                                "appointmentDate": "%s",
                                "description": "Team meeting"
                            }
                            """, futureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("100"))
                .andExpect(jsonPath("$.description").value("Team meeting"));
    }

    @Test
    void createAppointment_withProjectAndTaskIdsEchoesAssignments() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "linked-apt",
                                "appointmentDate": "%s",
                                "description": "Linked appointment",
                                "projectId": "proj-42",
                                "taskId": "task-99"
                            }
                            """, futureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value("proj-42"))
                .andExpect(jsonPath("$.taskId").value("task-99"));
    }

    @Test
    void getAllAppointments_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllAppointments_withData_returnsList() throws Exception {
        createTestAppointment("101", "First appointment");
        createTestAppointment("102", "Second appointment");

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockAppUser
    void getAllAppointments_allFlagRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/appointments").param("all", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only administrators can access all users' appointments"));
    }

    @Test
    @WithMockAppUser(username = "admin", role = Role.ADMIN)
    void getAllAppointments_allFlagReturnsAllForAdmin() throws Exception {
        addAppointmentForUser("alpha", Role.USER, "alpha-apt");
        addAppointmentForUser("beta", Role.USER, "beta-apt");
        addAppointmentForUser("admin", Role.ADMIN, "admin-apt");

        mockMvc.perform(get("/api/v1/appointments").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getAppointmentById_exists_returns200() throws Exception {
        createTestAppointment("103", "Find this appointment");

        mockMvc.perform(get("/api/v1/appointments/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("103"))
                .andExpect(jsonPath("$.description").value("Find this appointment"));
    }

    @Test
    void updateAppointment_exists_returns200() throws Exception {
        createTestAppointment("104", "Old description");

        mockMvc.perform(put("/api/v1/appointments/104")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "104",
                                "appointmentDate": "%s",
                                "description": "Updated description"
                            }
                            """, futureDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void deleteAppointment_exists_returns204() throws Exception {
        createTestAppointment("105", "Delete me");

        mockMvc.perform(delete("/api/v1/appointments/105")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/appointments/105"))
                .andExpect(status().isNotFound());
    }

    // ==================== Not Found Tests ====================

    @Test
    void getAppointmentById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: notfound"));
    }

    @Test
    void updateAppointment_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/appointments/missing")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "missing",
                                "appointmentDate": "%s",
                                "description": "Test description"
                            }
                            """, futureDate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: missing"));
    }

    @Test
    void deleteAppointment_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/notfound")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createAppointment_duplicateId_returns409() throws Exception {
        createTestAppointment("106", "First appointment");

        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "106",
                                "appointmentDate": "%s",
                                "description": "Different appointment"
                            }
                            """, futureDate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Appointment with id '106' already exists"));
    }

    // ==================== Validation Error Tests (Parameterized) ====================

    @ParameterizedTest(name = "create appointment with invalid {0}")
    @MethodSource("invalidAppointmentInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void createAppointment_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        // fieldName is used in test display name via {0} placeholder above
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidAppointmentInputs() {
        final String futureDate = DATE_FORMATTER.format(Instant.now().plus(1, ChronoUnit.DAYS));

        return Stream.of(
                // ID validation
                Arguments.of("id (blank)", String.format("""
                    {"id": "", "appointmentDate": "%s", "description": "Test description"}
                    """, futureDate), "id"),
                Arguments.of("id (too long)", String.format("""
                    {"id": "12345678901", "appointmentDate": "%s", "description": "Test description"}
                    """, futureDate), "id"),

                // description validation
                Arguments.of("description (blank)", String.format("""
                    {"id": "100", "appointmentDate": "%s", "description": ""}
                    """, futureDate), "description"),
                Arguments.of("description (too long)", String.format("""
                    {"id": "100", "appointmentDate": "%s", "description": "123456789012345678901234567890123456789012345678901"}
                    """, futureDate), "description")
        );
    }

    // ==================== Date Validation Tests ====================

    @Test
    void createAppointment_pastDate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "200",
                                "appointmentDate": "%s",
                                "description": "Past appointment"
                            }
                            """, pastDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("appointmentDate")));
    }

    @Test
    void createAppointment_nullDate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "201",
                                "appointmentDate": null,
                                "description": "Null date appointment"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("appointmentDate")));
    }

    // ==================== Boundary Tests (Bean Validation accuracy) ====================

    @Test
    void createAppointment_exactlyMaxLengthFields_accepted() throws Exception {
        // Test exact boundaries: id=10, description=50
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "1234567890",
                                "appointmentDate": "%s",
                                "description": "12345678901234567890123456789012345678901234567890"
                            }
                            """, futureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234567890"));
    }

    @Test
    void createAppointment_idOneOverMax_rejected() throws Exception {
        // id max is 10, this is 11
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "12345678901",
                                "appointmentDate": "%s",
                                "description": "Test description"
                            }
                            """, futureDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    void createAppointment_descriptionOneOverMax_rejected() throws Exception {
        // description max is 50, this is 51
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "202",
                                "appointmentDate": "%s",
                                "description": "123456789012345678901234567890123456789012345678901"
                            }
                            """, futureDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("description")));
    }

    // ==================== Archive/Unarchive Tests ====================

    @Test
    void archiveAppointment_exists_returns200() throws Exception {
        createTestAppointment("arch-1", "Archive me");

        mockMvc.perform(patch("/api/v1/appointments/arch-1/archive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("arch-1"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archiveAppointment_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/missing/archive")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: missing"));
    }

    @Test
    void unarchiveAppointment_exists_returns200() throws Exception {
        createTestAppointment("unarch-1", "Archive then unarchive me");

        // First archive
        mockMvc.perform(patch("/api/v1/appointments/unarch-1/archive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // Then unarchive
        mockMvc.perform(patch("/api/v1/appointments/unarch-1/unarchive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("unarch-1"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void unarchiveAppointment_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/missing/unarchive")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: missing"));
    }

    @Test
    void archiveAppointment_alreadyArchived_remainsArchived() throws Exception {
        createTestAppointment("dbl-arch", "Archive twice");

        // Archive first time
        mockMvc.perform(patch("/api/v1/appointments/dbl-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // Archive again - should still succeed and remain archived
        mockMvc.perform(patch("/api/v1/appointments/dbl-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void unarchiveAppointment_notArchived_remainsUnarchived() throws Exception {
        createTestAppointment("not-arch", "Never archived");

        // Unarchive without archiving first - should still succeed
        mockMvc.perform(patch("/api/v1/appointments/not-arch/unarchive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void archivedAppointment_stillRetrievableById() throws Exception {
        createTestAppointment("get-arch", "Get after archive");

        mockMvc.perform(patch("/api/v1/appointments/get-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk());

        // Should still be retrievable
        mockMvc.perform(get("/api/v1/appointments/get-arch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("get-arch"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archivedAppointment_includedInGetAll() throws Exception {
        createTestAppointment("list-arch", "Show in list when archived");

        mockMvc.perform(patch("/api/v1/appointments/list-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk());

        // Should still appear in list
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("list-arch"))
                .andExpect(jsonPath("$[0].archived").value(true));
    }

    @Test
    void archivedAppointment_canBeDeleted() throws Exception {
        createTestAppointment("del-arch", "Delete after archive");

        mockMvc.perform(patch("/api/v1/appointments/del-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/appointments/del-arch")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/appointments/del-arch"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedAppointment_canBeUpdated() throws Exception {
        createTestAppointment("upd-arch", "Update after archive");

        mockMvc.perform(patch("/api/v1/appointments/upd-arch/archive")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/appointments/upd-arch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "upd-arch",
                                "appointmentDate": "%s",
                                "description": "Updated while archived"
                            }
                            """, futureDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated while archived"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    // ==================== Malformed JSON Test ====================

    @Test
    void createAppointment_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    // ==================== Helper Methods ====================

    private void createTestAppointment(
            final String id,
            final String description) throws Exception {

        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "%s",
                                "appointmentDate": "%s",
                                "description": "%s"
                            }
                            """, id, futureDate, description)))
                .andExpect(status().isCreated());
    }

    private void addAppointmentForUser(final String username, final Role role, final String id) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        appointmentService.addAppointment(
                new Appointment(id, new Date(System.currentTimeMillis() + 86_400_000L), "Desc-" + id));
    }
}
