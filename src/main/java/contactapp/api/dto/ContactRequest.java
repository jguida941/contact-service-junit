package contactapp.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import static contactapp.domain.Validation.MAX_ADDRESS_LENGTH;
import static contactapp.domain.Validation.MAX_ID_LENGTH;
import static contactapp.domain.Validation.MAX_NAME_LENGTH;

/**
 * Request DTO for creating or updating a Contact.
 *
 * <p>Uses Bean Validation annotations for API-layer validation. Domain-level
 * validation via {@link contactapp.domain.Validation} acts as a backup layer
 * when the Contact constructor is called.
 *
 * <p><strong>Note:</strong> Bean Validation runs on the raw request payload. Inputs
 * that contain only whitespace are rejected before the domain layer sees them.
 * After API validation succeeds the domain constructors trim and re-validate the
 * values so persisted state stays normalized.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>id: required, 1-10 characters</li>
 *   <li>firstName: required, 1-10 characters</li>
 *   <li>lastName: required, 1-10 characters</li>
 *   <li>phone: required, exactly 10 digits</li>
 *   <li>address: required, 1-30 characters</li>
 * </ul>
 *
 * @param id        unique identifier for the contact
 * @param firstName contact's first name
 * @param lastName  contact's last name
 * @param phone     contact's phone number (10 digits)
 * @param address   contact's address
 */
public record ContactRequest(
        @Schema(description = "Contact ID")
        @NotBlank(message = "Contact ID must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "Contact ID length must be between {min} and {max}")
        String id,

        @Schema(description = "First name")
        @NotBlank(message = "First Name must not be null or blank")
        @Size(min = 1, max = MAX_NAME_LENGTH, message = "First Name length must be between {min} and {max}")
        String firstName,

        @Schema(description = "Last name")
        @NotBlank(message = "Last Name must not be null or blank")
        @Size(min = 1, max = MAX_NAME_LENGTH, message = "Last Name length must be between {min} and {max}")
        String lastName,

        @NotBlank(message = "Phone must not be null or blank")
        @Pattern(regexp = "\\d{10}", message = "Phone must be exactly 10 digits")
        String phone,

        @Schema(description = "Address")
        @NotBlank(message = "Address must not be null or blank")
        @Size(min = 1, max = MAX_ADDRESS_LENGTH, message = "Address length must be between {min} and {max}")
        String address
) {
}
