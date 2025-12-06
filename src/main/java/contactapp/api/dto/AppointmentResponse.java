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
 * <p>The appointmentDate is serialized as ISO 8601 with milliseconds and offset:
 * {@code "2024-12-25T10:30:00.000+00:00"} (UTC).
 * This matches the format accepted by {@link AppointmentRequest}.
 *
 * @param id              the appointment's unique identifier
 * @param appointmentDate the appointment date/time
 * @param description     the appointment description
 * @param projectId       the associated project ID (nullable)
 * @param taskId          the associated task ID (nullable)
 * @param isPast          true if the appointment date is in the past
 * @param archived        true if the appointment has been archived
 */
public record AppointmentResponse(
        String id,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Date appointmentDate,
        String description,
        String projectId,
        String taskId,
        boolean isPast,
        boolean archived
) {
    /**
     * Compact constructor that makes a defensive copy of the mutable Date.
     */
    public AppointmentResponse {
        // Defensive copy of mutable Date to satisfy SpotBugs EI_EXPOSE_REP2
        appointmentDate = appointmentDate == null ? null : new Date(appointmentDate.getTime());
    }

    /**
     * Returns a defensive copy of the appointment date.
     *
     * @return a copy of the appointment date, or null if not set
     */
    @Override
    public Date appointmentDate() {
        // Defensive copy to satisfy SpotBugs EI_EXPOSE_REP
        return appointmentDate == null ? null : new Date(appointmentDate.getTime());
    }

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
                appointment.getDescription(),
                appointment.getProjectId(),
                appointment.getTaskId(),
                appointment.isPast(),
                appointment.isArchived()
        );
    }
}
