package contactapp;

import contactapp.service.AppointmentService;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentService appointmentService;

    /** Future date for valid appointments (1 day from now). */
    private String futureDate;

    /** Past date for invalid appointments (1 day ago). */
    private String pastDate;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
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

        mockMvc.perform(delete("/api/v1/appointments/105"))
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
        mockMvc.perform(delete("/api/v1/appointments/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createAppointment_duplicateId_returns409() throws Exception {
        createTestAppointment("106", "First appointment");

        mockMvc.perform(post("/api/v1/appointments")
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
    void createAppointment_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {

        mockMvc.perform(post("/api/v1/appointments")
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

    // ==================== Malformed JSON Test ====================

    @Test
    void createAppointment_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
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
}
