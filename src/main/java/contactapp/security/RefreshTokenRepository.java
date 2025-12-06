package contactapp.security;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for RefreshToken persistence (ADR-0052 Phase B).
 *
 * <p>Provides methods for token lookup, revocation, and cleanup:
 * <ul>
 *   <li>{@link #findByTokenAndRevokedFalse} - Find valid token for refresh</li>
 *   <li>{@link #revokeAllByUserId} - Revoke all tokens for a user (logout/password change)</li>
 *   <li>{@link #deleteAllExpired} - Cleanup job removes old tokens</li>
 * </ul>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a non-revoked token by its value with eagerly fetched user.
     * Caller must also check expiry date for full validation.
     *
     * @param token the token string
     * @return the refresh token if found and not revoked
     */
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token AND rt.revoked = false")
    Optional<RefreshToken> findByTokenAndRevokedFalse(@Param("token") String token);

    /**
     * Finds a token by its value (regardless of revoked status) with eagerly fetched user.
     *
     * @param token the token string
     * @return the refresh token if found
     */
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token")
    Optional<RefreshToken> findByToken(@Param("token") String token);

    /**
     * Revokes all tokens for a user.
     * Used on logout, password change, or security events.
     *
     * @param userId the user's UUID
     * @return the number of tokens revoked
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Deletes all expired tokens (cleanup job).
     * Tokens are deleted if their expiry date is before the cutoff time.
     * Note: Revoked tokens are kept until they expire naturally for audit purposes.
     *
     * @param cutoffTime tokens expired before this time are deleted
     * @return the number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :cutoffTime")
    int deleteAllExpired(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Counts active (non-revoked, non-expired) tokens for a user.
     * Should be 0 or 1 in single-session model.
     *
     * @param userId the user's UUID
     * @param now the current time for expiry check
     * @return count of active tokens
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt "
            + "WHERE rt.user.id = :userId AND rt.revoked = false AND rt.expiryDate > :now")
    long countActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Finds and locks a non-revoked token for atomic validate-and-revoke.
     *
     * <p>Uses pessimistic write lock to prevent TOCTOU race conditions during
     * token rotation. Concurrent refresh requests with the same token will
     * serialize at the database level.
     *
     * @param token the token string
     * @return the refresh token if found and not revoked, locked for update
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token AND rt.revoked = false")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);
}
