package contactapp.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>appointmentDate: required, must be in the future or present (ISO 8601 format)</li>
 *   <li>description: required, 1-50 characters</li>
 * </ul>
 *
 * <h2>Date Format</h2>
 * <p>The appointmentDate accepts ISO 8601 with milliseconds and offset:
 * {@code "2024-12-25T10:30:00.000+00:00"} (UTC shown here).
 *
 * @param id              unique identifier for the appointment
 * @param appointmentDate appointment date/time (must be now or in future)
 * @param description     appointment description
 */
public record AppointmentRequest(
        @NotBlank(message = "id must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "id length must be between {min} and {max}")
        String id,

        @NotNull(message = "appointmentDate must not be null")
        @FutureOrPresent(message = "appointmentDate must not be in the past")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Date appointmentDate,

        @NotBlank(message = "description must not be null or blank")
        @Size(min = 1, max = MAX_DESCRIPTION_LENGTH, message = "description length must be between {min} and {max}")
        String description
) {
}
