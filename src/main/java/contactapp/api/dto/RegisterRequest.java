package contactapp.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import static contactapp.domain.Validation.MAX_EMAIL_LENGTH;
import static contactapp.domain.Validation.MAX_PASSWORD_LENGTH;
import static contactapp.domain.Validation.MAX_USERNAME_LENGTH;
import static contactapp.domain.Validation.MIN_PASSWORD_LENGTH;

/**
 * Request DTO for user registration.
 *
 * <p>Uses Bean Validation annotations for API-layer validation.
 * Domain-level validation via {@link contactapp.domain.Validation} acts as a backup layer
 * when the User entity is constructed.
 *
 * <h2>Field Constraints</h2>
 * <ul>
 *   <li>username: required, 1-50 characters</li>
 *   <li>email: required, 1-100 characters, valid email format</li>
 *   <li>password: required, 8-255 characters, must contain uppercase, lowercase, and digit</li>
 * </ul>
 *
 * @param username the desired username
 * @param email the user's email address
 * @param password the raw password (will be hashed before storage)
 */
@Schema(description = "Registration data for new user")
public record RegisterRequest(

        @Schema(description = "Desired username", example = "johndoe")
        @NotBlank(message = "Username must not be null or blank")
        @Size(min = 1, max = MAX_USERNAME_LENGTH, message = "Username length must be between {min} and {max}")
        String username,

        @Schema(description = "Email address", example = "john.doe@example.com")
        @NotBlank(message = "Email must not be null or blank")
        @Size(min = 1, max = MAX_EMAIL_LENGTH, message = "Email length must be between {min} and {max}")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Password (8+ chars, uppercase, lowercase, digit)",
                example = "SecurePass123")
        @NotBlank(message = "Password must not be null or blank")
        @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH,
                message = "Password length must be between {min} and {max}")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, "
                        + "one lowercase letter, and one digit")
        String password
) {
}
