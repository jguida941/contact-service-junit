-- =============================================================================
-- V17: Create refresh_tokens table (ADR-0052 Phase B)
-- =============================================================================
-- Stores refresh tokens for long-lived sessions with server-side revocation.
-- Key properties:
--   - user_id is UUID foreign key to users table
--   - token is unique opaque string (not JWT)
--   - Supports single-session model (revoke all on new login)
--   - Cleanup job deletes expired tokens periodically
-- =============================================================================

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Note: No explicit index on token - UNIQUE constraint on line 15 creates one automatically

-- Index for user lookup (revoke all user tokens)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Index for cleanup job (find expired tokens)
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens(expiry_date);

-- Composite index for finding valid tokens by user
-- Column order: equality (user_id) → range (expiry_date) → low-cardinality (revoked)
CREATE INDEX idx_refresh_tokens_user_valid ON refresh_tokens(user_id, expiry_date, revoked);

COMMENT ON TABLE refresh_tokens IS 'Stores refresh tokens for long-lived sessions (ADR-0052)';
COMMENT ON COLUMN refresh_tokens.token IS 'Opaque random token string (not JWT)';
COMMENT ON COLUMN refresh_tokens.revoked IS 'True if token has been revoked (logout/security event)';
