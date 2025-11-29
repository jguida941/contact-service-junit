package contactapp.api.dto;

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
        @NotBlank(message = "id must not be null or blank")
        @Size(min = 1, max = MAX_ID_LENGTH, message = "id length must be between {min} and {max}")
        String id,

        @NotBlank(message = "firstName must not be null or blank")
        @Size(min = 1, max = MAX_NAME_LENGTH, message = "firstName length must be between {min} and {max}")
        String firstName,

        @NotBlank(message = "lastName must not be null or blank")
        @Size(min = 1, max = MAX_NAME_LENGTH, message = "lastName length must be between {min} and {max}")
        String lastName,

        @NotBlank(message = "phone must not be null or blank")
        @Pattern(regexp = "\\d{10}", message = "phone must be exactly 10 digits")
        String phone,

        @NotBlank(message = "address must not be null or blank")
        @Size(min = 1, max = MAX_ADDRESS_LENGTH, message = "address length must be between {min} and {max}")
        String address
) {
}
