-- =============================================================================
-- V17: Create refresh_tokens table (ADR-0052 Phase B) - H2 Version
-- =============================================================================
-- H2-compatible version of the refresh tokens table.
-- Uses TIMESTAMP instead of TIMESTAMP WITH TIME ZONE for H2 compatibility.
-- =============================================================================

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Note: No explicit index on token - UNIQUE constraint on line 11 creates one automatically

-- Index for user lookup (revoke all user tokens)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Index for cleanup job (find expired tokens)
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens(expiry_date);

-- Composite index for finding valid tokens by user
-- Column order: equality (user_id) → range (expiry_date) → low-cardinality (revoked)
CREATE INDEX idx_refresh_tokens_user_valid ON refresh_tokens(user_id, expiry_date, revoked);
