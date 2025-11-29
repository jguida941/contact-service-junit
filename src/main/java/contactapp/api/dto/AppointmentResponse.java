package contactapp.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import contactapp.domain.Appointment;
import java.util.Date;
import java.util.Objects;

/**
 * Response DTO for Appointment data returned by the API.
 *
 * <p>Provides a clean separation between the domain object and the API contract.
 * Uses a factory method to convert from the domain object.
 *
 * <h2>Date Format</h2>
 * <p>The appointmentDate is serialized as ISO 8601 format.
 *
 * @param id              the appointment's unique identifier
 * @param appointmentDate the appointment date/time
 * @param description     the appointment description
 */
public record AppointmentResponse(
        String id,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        Date appointmentDate,
        String description
) {
    /**
     * Creates an AppointmentResponse from an Appointment domain object.
     *
     * @param appointment the domain object to convert (must not be null)
     * @return a new AppointmentResponse with the appointment's data
     * @throws NullPointerException if appointment is null
     */
    public static AppointmentResponse from(final Appointment appointment) {
        Objects.requireNonNull(appointment, "appointment must not be null");
        return new AppointmentResponse(
                appointment.getAppointmentId(),
                appointment.getAppointmentDate(),
                appointment.getDescription()
        );
    }
}
