package contactapp.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import contactapp.domain.Validation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * User entity for authentication and authorization.
 * Implements Spring Security's UserDetails interface for seamless integration.
 *
 * <p>Field constraints are defined in {@link Validation}:
 * <ul>
 *   <li>username: 1-{@value Validation#MAX_USERNAME_LENGTH} characters</li>
 *   <li>email: 1-{@value Validation#MAX_EMAIL_LENGTH} characters</li>
 *   <li>password: 1-{@value Validation#MAX_PASSWORD_LENGTH} characters (stores hash)</li>
 * </ul>
 *
 * @see Validation#MAX_USERNAME_LENGTH
 * @see Validation#MAX_EMAIL_LENGTH
 * @see Validation#MAX_PASSWORD_LENGTH
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    private static final long serialVersionUID = 1L;
    /** BCrypt hash length is always 60 characters: $2a$10$ + 22-char salt + 31-char hash. */
    private static final int BCRYPT_HASH_LENGTH = 60;
    /** Stricter BCrypt pattern: prefix + 2-digit cost + $ + 53 chars (salt+hash). */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    @Id
    private UUID id;

    @Column(unique = true, nullable = false, length = Validation.MAX_USERNAME_LENGTH)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = Validation.MAX_PASSWORD_LENGTH)
    private String password;

    @Column(unique = true, nullable = false, length = Validation.MAX_EMAIL_LENGTH)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Validation.MAX_ROLE_LENGTH)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor for JPA.
     */
    protected User() {
    }

    /**
     * Creates a new user with the given credentials.
     * Validates all fields before assignment.
     *
     * @param username the username (1-50 chars)
     * @param email the email address (1-100 chars)
     * @param password the encoded password hash (1-255 chars). MUST be pre-encoded with a secure
     *                 password encoder such as
     *                 {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}.
     *                 Example: {@code passwordEncoder.encode(rawPassword)}. Raw passwords are rejected.
     * @param role the user role
     * @throws IllegalArgumentException if any field fails validation
     */
    public User(final String username, final String email, final String password, final Role role) {
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.password = requireBcryptHash(password);
        this.role = requireRole(role);
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        final Role resolvedRole = Objects.requireNonNull(role, "User role must not be null");
        return List.of(new SimpleGrantedAuthority("ROLE_" + resolvedRole.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Package-private setters for JPA and testing

    void setId(final UUID id) {
        this.id = id;
    }

    void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    private static String normalizeUsername(final String username) {
        return Validation.validateTrimmedLength(username, "Username", 1, Validation.MAX_USERNAME_LENGTH);
    }

    private static String normalizeEmail(final String email) {
        Validation.validateEmail(email, "Email");
        return email.trim();
    }

    private static String requireBcryptHash(final String password) {
        Validation.validateLength(password, "Password", 1, Validation.MAX_PASSWORD_LENGTH);
        if (!BCRYPT_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "Password must be a BCrypt hash starting with $2a$, $2b$, or $2y$");
        }
        return password;
    }

    private static Role requireRole(final Role role) {
        return Validation.validateNotNull(role, "Role");
    }
}
