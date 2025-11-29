package contactapp.api;

import contactapp.api.dto.AppointmentRequest;
import contactapp.api.dto.AppointmentResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Appointment;
import contactapp.service.AppointmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
 * </ul>
 *
 * <h2>Date Handling</h2>
 * <p>Dates are accepted and returned in ISO 8601 format: {@code yyyy-MM-dd'T'HH:mm:ss}
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
@RequestMapping("/api/v1/appointments")
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
     * @throws DuplicateResourceException if an appointment with the given ID already exists
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody final AppointmentRequest request) {
        final Appointment appointment = new Appointment(
                request.id(),
                request.appointmentDate(),
                request.description()
        );

        if (!appointmentService.addAppointment(appointment)) {
            throw new DuplicateResourceException(
                    "Appointment with id '" + request.id() + "' already exists");
        }

        return AppointmentResponse.from(appointment);
    }

    /**
     * Returns all appointments.
     *
     * @return list of all appointments
     */
    @GetMapping
    public List<AppointmentResponse> getAll() {
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
    @GetMapping("/{id}")
    public AppointmentResponse getById(@PathVariable final String id) {
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
    @PutMapping("/{id}")
    public AppointmentResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final AppointmentRequest request) {

        if (!appointmentService.updateAppointment(
                id,
                request.appointmentDate(),
                request.description())) {
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
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable final String id) {
        if (!appointmentService.deleteAppointment(id)) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }
    }
}
