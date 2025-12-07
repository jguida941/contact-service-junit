package contactapp.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import static contactapp.domain.Validation.MAX_DESCRIPTION_LENGTH;
import static contactapp.domain.Validation.MAX_ID_LENGTH;

/**
 * Request DTO for creating or updating an Appointment.
 *
 * <p>Uses Bean Validation annotations for API-layer validation. Domain-level
 * validation via {@link contactapp.domain.Validation} acts as a backup layer
 * when the Appointment constructor is called.
 *
 * <p><strong>Note:</strong> Bean Validation happens before domain trimming. The API
 * layer therefore rejects whitespace-only values, and any accepted inputs are trimmed
 * and re-validated inside the domain constructor.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>appointmentDate: required, must be in the future or present (ISO 8601 format)</li>
 *   <li>description: required, 1-50 characters</li>
 *   <li>projectId: optional, max 10 characters</li>
 *   <li>taskId: optional, max 10 characters</li>
 * </ul>
 *
 * <h2>Date Format</h2>
 * <p>The appointmentDate accepts ISO 8601 with milliseconds and offset:
 * {@code "2024-12-25T10:30:00.000+00:00"} (UTC shown here).
 *
 * @param id              unique identifier for the appointment
 * @param appointmentDate appointment date/time (must be now or in future)
 * @param description     appointment description
 * @param projectId       associated project ID (optional)
 * @param taskId          associated task ID (optional)
 */
public record AppointmentRequest(
        @Schema(description = "Appointment ID")
        @NotBlank(message = "Appointment ID must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "Appointment ID length must be between {min} and {max}")
        String id,

        @NotNull(message = "Appointment Date must not be null")
        @FutureOrPresent(message = "Appointment Date must not be in the past")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Date appointmentDate,

        @Schema(description = "Appointment description")
        @NotBlank(message = "Description must not be null or blank")
        @Size(min = 1, max = MAX_DESCRIPTION_LENGTH, message = "Description length must be between {min} and {max}")
        String description,

        @Schema(description = "Associated project ID (optional)")
        @Size(max = MAX_ID_LENGTH, message = "Project ID length must not exceed {max}")
        String projectId,

        @Schema(description = "Associated task ID (optional)")
        @Size(max = MAX_ID_LENGTH, message = "Task ID length must not exceed {max}")
        String taskId
) {
    /**
     * Compact constructor that makes a defensive copy of the mutable Date.
     */
    public AppointmentRequest {
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
}
