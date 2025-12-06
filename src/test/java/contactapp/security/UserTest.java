package contactapp.security;

import contactapp.domain.Validation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the User entity validation.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Successful creation with valid data</li>
 *   <li>Invalid inputs cause IllegalArgumentException with specific messages</li>
 *   <li>UserDetails interface implementation</li>
 *   <li>Boundary conditions for field lengths</li>
 * </ul>
 */
class UserTest {

    private static final String VALID_USERNAME = "testuser";
    private static final String VALID_EMAIL = "test@example.com";
    /** Valid BCrypt hash format: $2a$10$ prefix + 53 chars = 60 total. Fake hash for testing only. */
    private static final String VALID_PASSWORD = "$2a$10$TESTHASHaaaaaaaaaaaaaaBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String MIN_VALID_EMAIL = "a@b.co";
    private static final String EMAIL_DOMAIN = "@example.com";

    // ==================== Successful Creation Tests ====================

    @Test
    void testSuccessfulCreation() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getUsername()).isEqualTo(VALID_USERNAME);
        assertThat(user.getEmail()).isEqualTo(VALID_EMAIL);
        assertThat(user.getPassword()).isEqualTo(VALID_PASSWORD);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void testCreationWithAdminRole() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void testConstructorTrimsUsernameAndEmail() {
        User user = new User("  testuser  ", "  test@example.com  ", VALID_PASSWORD, Role.USER);

        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
    }

    // ==================== UserDetails Interface Tests ====================

    @Test
    void testGetAuthoritiesReturnsRoleWithPrefix() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getAuthorities())
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void testGetAuthoritiesForAdmin() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.ADMIN);

        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void testAccountStatusMethodsReturnTrue() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    /**
     * Ensures {@link User#isEnabled()} returns the actual field value rather
     * than a hardcoded constant. PIT flagged a surviving mutant where
     * returning {@code true} unconditionally went undetected.
     */
    @Test
    void testIsEnabledReturnsFalseWhenDisabled() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);
        user.setEnabled(false);

        assertThat(user.isEnabled()).isFalse();
    }

    // ==================== Username Validation Tests ====================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void testUsernameBlankThrows(String username) {
        assertThatThrownBy(() -> new User(username, VALID_EMAIL, VALID_PASSWORD, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void testUsernameAtMaxLength() {
        String maxUsername = "a".repeat(Validation.MAX_USERNAME_LENGTH);
        User user = new User(maxUsername, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getUsername()).hasSize(Validation.MAX_USERNAME_LENGTH);
    }

    @Test
    void testUsernameOverMaxLengthThrows() {
        String tooLong = "a".repeat(Validation.MAX_USERNAME_LENGTH + 1);

        assertThatThrownBy(() -> new User(tooLong, VALID_EMAIL, VALID_PASSWORD, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username")
                .hasMessageContaining("length");
    }

    // ==================== Email Validation Tests ====================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void testEmailBlankThrows(String email) {
        assertThatThrownBy(() -> new User(VALID_USERNAME, email, VALID_PASSWORD, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void testEmailAtMaxLength() {
        String maxEmail = emailOfLength(Validation.MAX_EMAIL_LENGTH);
        User user = new User(VALID_USERNAME, maxEmail, VALID_PASSWORD, Role.USER);

        assertThat(user.getEmail()).hasSize(Validation.MAX_EMAIL_LENGTH);
    }

    @Test
    void testEmailOverMaxLengthThrows() {
        String tooLong = emailOfLength(Validation.MAX_EMAIL_LENGTH + 1);

        assertThatThrownBy(() -> new User(VALID_USERNAME, tooLong, VALID_PASSWORD, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email")
                .hasMessageContaining("length");
    }

    @Test
    void testEmailMinimalValidLength() {
        User user = new User(VALID_USERNAME, MIN_VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getEmail()).isEqualTo(MIN_VALID_EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plainaddress",
            "missing-at.example.com",
            "user@",
            "user@domain",
            "user@domain.",
            "user@domain..com"
    })
    void testEmailInvalidFormatThrows(String email) {
        assertThatThrownBy(() -> new User(VALID_USERNAME, email, VALID_PASSWORD, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email")
                .hasMessageContaining("valid email");
    }

    // ==================== Password Validation Tests ====================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void testPasswordBlankThrows(String password) {
        assertThatThrownBy(() -> new User(VALID_USERNAME, VALID_EMAIL, password, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
    }

    @Test
    void testPasswordAcceptsBcryptHash() {
        // BCrypt hashes are always exactly 60 characters
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getPassword()).hasSize(60);
    }

    @Test
    void testPasswordOverMaxLengthThrows() {
        String tooLong = "a".repeat(Validation.MAX_PASSWORD_LENGTH + 1);

        assertThatThrownBy(() -> new User(VALID_USERNAME, VALID_EMAIL, tooLong, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password")
                .hasMessageContaining("length");
    }

    @Test
    void testPasswordMustBeBcryptHash() {
        assertThatThrownBy(() -> new User(VALID_USERNAME, VALID_EMAIL, "plain-text", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BCrypt hash");
    }

    @Test
    void testIdGetterReturnsAssignedValue() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);
        UUID testId = UUID.randomUUID();
        user.setId(testId);

        assertThat(user.getId()).isEqualTo(testId);
    }

    @Test
    void testTimestampLifecycleCallbacksPopulateValues() {
        User user = new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        user.onCreate();
        Instant created = user.getCreatedAt();
        Instant firstUpdated = user.getUpdatedAt();

        assertThat(created).isNotNull();
        assertThat(firstUpdated).isEqualTo(created);

        user.onUpdate();
        Instant secondUpdated = user.getUpdatedAt();

        assertThat(secondUpdated).isNotNull();
        assertThat(secondUpdated).isAfterOrEqualTo(created);
    }

    // ==================== Role Validation Tests ====================

    @Test
    void testNullRoleThrows() {
        assertThatThrownBy(() -> new User(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role");
    }

    // ==================== Boundary Tests ====================

    @ParameterizedTest
    @CsvSource({
            "1, valid single char username",
            "50, valid max length username"
    })
    void testUsernameBoundaryLengths(int length, String description) {
        assertThat(description).isNotBlank();
        String username = "a".repeat(length);
        User user = new User(username, VALID_EMAIL, VALID_PASSWORD, Role.USER);

        assertThat(user.getUsername()).hasSize(length);
    }

    private static String emailOfLength(int length) {
        if (length <= EMAIL_DOMAIN.length()) {
            throw new IllegalArgumentException("Length must be greater than domain size");
        }
        int localPartLength = length - EMAIL_DOMAIN.length();
        return "a".repeat(localPartLength) + EMAIL_DOMAIN;
    }
}
