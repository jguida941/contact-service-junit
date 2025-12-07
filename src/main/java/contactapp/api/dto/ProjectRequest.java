package contactapp.api.dto;

import contactapp.domain.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import static contactapp.domain.Validation.MAX_ID_LENGTH;
import static contactapp.domain.Validation.MAX_PROJECT_DESCRIPTION_LENGTH;
import static contactapp.domain.Validation.MAX_PROJECT_NAME_LENGTH;

/**
 * Request DTO for creating or updating a Project.
 *
 * <p>Uses Bean Validation annotations for API-layer validation. Domain-level
 * validation via {@link contactapp.domain.Validation} acts as a backup layer
 * when the Project constructor is called.
 *
 * <p><strong>Note:</strong> Bean Validation runs on the raw request payload. Inputs
 * that contain only whitespace are rejected before the domain layer sees them.
 * After API validation succeeds the domain constructors trim and re-validate the
 * values so persisted state stays normalized.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>name: required, 1-50 characters</li>
 *   <li>description: optional, max 100 characters</li>
 *   <li>status: required, one of ACTIVE, ON_HOLD, COMPLETED, ARCHIVED</li>
 * </ul>
 *
 * @param id          unique identifier for the project
 * @param name        project name
 * @param description project description
 * @param status      project status
 */
public record ProjectRequest(
        @Schema(description = "Project ID")
        @NotBlank(message = "Project ID must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "Project ID length must be between {min} and {max}")
        String id,

        @Schema(description = "Project name")
        @NotBlank(message = "Project Name must not be null or blank")
        @Size(min = 1, max = MAX_PROJECT_NAME_LENGTH, message = "Project Name length must be between {min} and {max}")
        String name,

        @Schema(description = "Project description")
        @NotNull(message = "Description must not be null")
        @Size(max = MAX_PROJECT_DESCRIPTION_LENGTH, message = "Description length must not exceed {max}")
        String description,

        @Schema(description = "Project status")
        @NotNull(message = "Status must not be null")
        ProjectStatus status
) {
}
