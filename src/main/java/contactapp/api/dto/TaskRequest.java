package contactapp.api.dto;

import contactapp.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
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
 * <p><strong>Note:</strong> Bean Validation runs before the domain layer trims
 * values. Inputs consisting solely of whitespace are rejected by the API layer;
 * once validation passes, the domain constructors trim and re-validate.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>name: required, 1-20 characters</li>
 *   <li>description: required, 1-50 characters</li>
 *   <li>status: optional, defaults to TODO if not provided</li>
 *   <li>dueDate: optional, can be null</li>
 *   <li>projectId: optional, links task to a project</li>
 *   <li>assigneeId: optional, user ID to assign task to</li>
 * </ul>
 *
 * @param id          unique identifier for the task
 * @param name        task name
 * @param description task description
 * @param status      task status (optional, defaults to TODO)
 * @param dueDate     task due date (optional)
 * @param projectId   associated project ID (optional)
 * @param assigneeId  assignee user ID (optional)
 */
public record TaskRequest(
        @Schema(description = "Task ID")
        @NotBlank(message = "Task ID must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "Task ID length must be between {min} and {max}")
        String id,

        @Schema(description = "Task name")
        @NotBlank(message = "Task Name must not be null or blank")
        @Size(min = 1, max = MAX_TASK_NAME_LENGTH, message = "Task Name length must be between {min} and {max}")
        String name,

        @Schema(description = "Task description")
        @NotBlank(message = "Task Description must not be null or blank")
        @Size(min = 1, max = MAX_DESCRIPTION_LENGTH,
                message = "Task Description length must be between {min} and {max}")
        String description,

        @Schema(description = "Task status (defaults to TODO if not provided)")
        TaskStatus status,

        @Schema(description = "Task due date (optional)")
        LocalDate dueDate,

        @Schema(description = "Associated project ID (optional)")
        @Size(max = MAX_ID_LENGTH, message = "Project ID length must not exceed {max}")
        String projectId,

        @Schema(description = "Assignee user ID (optional)")
        UUID assigneeId
) {
}
