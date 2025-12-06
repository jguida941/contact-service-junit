package contactapp.api;

import contactapp.api.dto.AppointmentRequest;
import contactapp.api.dto.AppointmentResponse;
import contactapp.api.dto.ErrorResponse;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Appointment;
import contactapp.service.AppointmentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import static contactapp.domain.Validation.MAX_ID_LENGTH;

/**
 * REST controller for Appointment CRUD operations.
 *
 * <p>Provides endpoints at {@code /api/v1/appointments} per ADR-0016.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/v1/appointments - Create a new appointment (201 Created)</li>
 *   <li>GET /api/v1/appointments - List all appointments (200 OK)</li>
 *   <li>GET /api/v1/appointments/{id} - Get appointment by ID (200 OK / 404 Not Found)</li>
 *   <li>PUT /api/v1/appointments/{id} - Update appointment (200 OK / 404 Not Found)</li>
 *   <li>DELETE /api/v1/appointments/{id} - Delete appointment (204 No Content / 404 Not Found)</li>
 *   <li>PATCH /api/v1/appointments/{id}/archive - Archive appointment (200 OK / 404 Not Found)</li>
 *   <li>PATCH /api/v1/appointments/{id}/unarchive - Unarchive appointment (200 OK / 404 Not Found)</li>
 * </ul>
 *
 * <h2>Date Handling</h2>
 * <p>Dates are accepted and returned in ISO 8601 format with milliseconds and offset:
 * {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} (UTC timezone).
 *
 * <h2>Validation</h2>
 * <p>Uses two layers of validation:
 * <ol>
 *   <li>Bean Validation on request DTOs ({@code @Valid})</li>
 *   <li>Domain validation via {@link contactapp.domain.Validation} in Appointment constructor</li>
 * </ol>
 *
 * @see AppointmentRequest
 * @see AppointmentResponse
 * @see AppointmentService
 */
@RestController
@RequestMapping(value = "/api/v1/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Appointments", description = "Appointment CRUD operations")
@SecurityRequirement(name = "bearerAuth")
@Validated
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed singleton service is intentionally stored without copy"
)
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Creates a new AppointmentController with the given service.
     *
     * @param appointmentService the service for appointment operations
     */
    public AppointmentController(final AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Creates a new appointment.
     *
     * @param request the appointment data
     * @return the created appointment
     * @throws contactapp.api.exception.DuplicateResourceException if an appointment with the given ID already exists
     */
    @Operation(summary = "Create a new appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment created",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g., past date)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Appointment with this ID already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody final AppointmentRequest request) {
        final Appointment appointment = new Appointment(
                request.id(),
                request.appointmentDate(),
                request.description()
        );
        appointment.setProjectId(request.projectId());
        appointment.setTaskId(request.taskId());

        appointmentService.addAppointment(appointment);

        return AppointmentResponse.from(appointment);
    }

    /**
     * Returns all appointments for the authenticated user.
     *
     * <p>For regular users, returns only their appointments.
     * For ADMIN users with {@code ?all=true}, returns all appointments across all users.
     *
     * <p>Supports filtering by:
     * <ul>
     *   <li>{@code ?projectId=proj-1} - filter by associated project</li>
     *   <li>{@code ?taskId=task-1} - filter by associated task</li>
     * </ul>
     *
     * @param all if true and user is ADMIN, returns all appointments across all users
     * @param projectId filter by associated project ID
     * @param taskId filter by associated task ID
     * @return list of appointments
     */
    @Operation(summary = "Get all appointments",
            description = "Returns appointments for the authenticated user. "
                    + "ADMIN users can pass ?all=true to see all appointments across all users. "
                    + "Supports filtering by projectId and taskId.")
    @ApiResponse(responseCode = "200", description = "List of appointments")
    @GetMapping
    public List<AppointmentResponse> getAll(
            @Parameter(description = "If true and user is ADMIN, returns all appointments")
            @RequestParam(required = false, defaultValue = "false") final boolean all,
            @Parameter(description = "Filter by associated project ID")
            @RequestParam(required = false) @Size(max = MAX_ID_LENGTH) final String projectId,
            @Parameter(description = "Filter by associated task ID")
            @RequestParam(required = false) @Size(max = MAX_ID_LENGTH) final String taskId) {

        // Handle filtering
        if (projectId != null) {
            return appointmentService.getAppointmentsByProjectId(projectId).stream()
                    .map(AppointmentResponse::from)
                    .toList();
        }

        if (taskId != null) {
            return appointmentService.getAppointmentsByTaskId(taskId).stream()
                    .map(AppointmentResponse::from)
                    .toList();
        }

        // No filters - return all appointments
        if (all) {
            // Verify caller has ADMIN role before returning all users' data
            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            final boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!isAdmin) {
                throw new AccessDeniedException(
                        "Only administrators can access all users' appointments");
            }
            return appointmentService.getAllAppointmentsAllUsers().stream()
                    .map(AppointmentResponse::from)
                    .toList();
        }
        return appointmentService.getAllAppointments().stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /**
     * Returns an appointment by ID.
     *
     * @param id the appointment ID
     * @return the appointment
     * @throws ResourceNotFoundException if no appointment with the given ID exists
     */
    @Operation(summary = "Get appointment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment found",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public AppointmentResponse getById(
            @Parameter(
                    description = "Appointment ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        return appointmentService.getAppointmentById(id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));
    }

    /**
     * Updates an existing appointment.
     *
     * @param id      the appointment ID (from path)
     * @param request the updated appointment data
     * @return the updated appointment
     * @throws ResourceNotFoundException if no appointment with the given ID exists
     */
    @Operation(summary = "Update an existing appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment updated",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g., past date)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppointmentResponse update(
            @Parameter(
                    description = "Appointment ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id,
            @Valid @RequestBody final AppointmentRequest request) {

        if (!appointmentService.updateAppointment(
                id,
                request.appointmentDate(),
                request.description(),
                request.projectId(),
                request.taskId())) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }

        return getById(id);
    }

    /**
     * Deletes an appointment by ID.
     *
     * @param id the appointment ID
     * @throws ResourceNotFoundException if no appointment with the given ID exists
     */
    @Operation(summary = "Delete an appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Appointment deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(
                    description = "Appointment ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        if (!appointmentService.deleteAppointment(id)) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }
    }

    /**
     * Archives an appointment by ID.
     *
     * @param id the appointment ID
     * @return the updated appointment
     * @throws ResourceNotFoundException if no appointment with the given ID exists
     */
    @Operation(summary = "Archive an appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment archived",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public AppointmentResponse archive(
            @Parameter(
                    description = "Appointment ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        if (!appointmentService.archiveAppointment(id)) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }

        return getById(id);
    }

    /**
     * Unarchives an appointment by ID.
     *
     * @param id the appointment ID
     * @return the updated appointment
     * @throws ResourceNotFoundException if no appointment with the given ID exists
     */
    @Operation(summary = "Unarchive an appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment unarchived",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/unarchive")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public AppointmentResponse unarchive(
            @Parameter(
                    description = "Appointment ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        if (!appointmentService.unarchiveAppointment(id)) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }

        return getById(id);
    }
}
