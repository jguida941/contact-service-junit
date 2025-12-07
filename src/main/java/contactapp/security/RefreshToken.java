package contactapp.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity for storing refresh tokens (ADR-0052 Phase B).
 *
 * <p>Refresh tokens enable long-lived sessions without keeping access tokens
 * alive indefinitely. Key security properties:
 * <ul>
 *   <li>Stored in database for server-side revocation</li>
 *   <li>One active token per user (single-session model)</li>
 *   <li>Token rotation on each refresh (revoke old, create new)</li>
 *   <li>Automatic cleanup of expired tokens via scheduled job</li>
 * </ul>
 *
 * <p>The token value is an opaque random string, NOT a JWT. This allows
 * immediate revocation without token blacklisting.
 *
 * @see RefreshTokenRepository
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    /** Maximum length for token storage in database. */
    private static final int TOKEN_MAX_LENGTH = 255;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = TOKEN_MAX_LENGTH)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    /**
     * Default constructor for JPA.
     */
    protected RefreshToken() {
    }

    /**
     * Creates a new refresh token for a user.
     *
     * @param user the user this token belongs to
     * @param token the opaque token string (should be cryptographically random)
     * @param expiryDate when this token expires
     */
    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Validation in constructor is intentional; class has no finalizers"
    )
    public RefreshToken(final User user, final String token, final Instant expiryDate) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.token = Objects.requireNonNull(token, "Token must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        if (token.length() > TOKEN_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Token exceeds maximum length of " + TOKEN_MAX_LENGTH);
        }
        this.expiryDate = Objects.requireNonNull(expiryDate, "Expiry date must not be null");
        if (!expiryDate.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Expiry date must be in the future");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
    }

    /**
     * Checks if this token is valid (not revoked and not expired).
     *
     * @return true if the token can be used for refresh
     */
    public boolean isValid() {
        return !revoked && expiryDate.isAfter(Instant.now());
    }

    /**
     * Revokes this token, preventing future use.
     */
    public void revoke() {
        this.revoked = true;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "JPA entity relationship requires returning the actual User object"
    )
    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    // Package-private setters for testing

    void setId(final UUID id) {
        this.id = id;
    }

    /**
     * Creates a RefreshToken for testing purposes, bypassing validation.
     *
     * <p>This method should ONLY be used in tests to create tokens with
     * specific states (e.g., expired tokens) that the normal constructor
     * would reject. In production, JPA loads entities via reflection,
     * so expired tokens loaded from the database don't hit the constructor.
     *
     * @param user the user this token belongs to
     * @param token the token string
     * @param expiryDate the expiry date (may be in the past for testing)
     * @return a new RefreshToken instance
     */
    static RefreshToken createForTesting(final User user, final String token, final Instant expiryDate) {
        final RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.token = token;
        refreshToken.expiryDate = expiryDate;
        refreshToken.id = UUID.randomUUID();
        refreshToken.createdAt = Instant.now();
        return refreshToken;
    }
}
