package contactapp.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import static contactapp.domain.Validation.MAX_DESCRIPTION_LENGTH;
import static contactapp.domain.Validation.MAX_ID_LENGTH;
import static contactapp.domain.Validation.MAX_TASK_NAME_LENGTH;

/**
 * Request DTO for creating or updating a Task.
 *
 * <p>Uses Bean Validation annotations for API-layer validation. Domain-level
 * validation via {@link contactapp.domain.Validation} acts as a backup layer
 * when the Task constructor is called.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>name: required, 1-20 characters</li>
 *   <li>description: required, 1-50 characters</li>
 * </ul>
 *
 * @param id          unique identifier for the task
 * @param name        task name
 * @param description task description
 */
public record TaskRequest(
        @NotBlank(message = "id must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "id length must be between {min} and {max}")
        String id,

        @NotBlank(message = "name must not be null or blank")
        @Size(min = 1, max = MAX_TASK_NAME_LENGTH, message = "name length must be between {min} and {max}")
        String name,

        @NotBlank(message = "description must not be null or blank")
        @Size(min = 1, max = MAX_DESCRIPTION_LENGTH, message = "description length must be between {min} and {max}")
        String description
) {
}
