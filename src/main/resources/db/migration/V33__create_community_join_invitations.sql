CREATE TABLE community_join_invitations (
    id                    UUID         NOT NULL,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    created_by            UUID,
    updated_by            UUID,
    community_id          UUID         NOT NULL,
    -- SHA-256 hex digest of the raw token. The raw token is never persisted.
    token_hash            VARCHAR(64)  NOT NULL,
    invitation_type       VARCHAR(30)  NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    label                 VARCHAR(150),
    distribution_channel  VARCHAR(30),
    expires_at            TIMESTAMP    NOT NULL,
    max_uses              INTEGER,
    used_count            INTEGER      NOT NULL DEFAULT 0,
    revoked_by            UUID,
    revoked_at            TIMESTAMP,
    last_used_at          TIMESTAMP,
    CONSTRAINT pk_community_join_invitations PRIMARY KEY (id)
);

ALTER TABLE community_join_invitations ADD CONSTRAINT fk_community_join_invitations_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE community_join_invitations ADD CONSTRAINT fk_community_join_invitations_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE community_join_invitations ADD CONSTRAINT fk_community_join_invitations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE community_join_invitations ADD CONSTRAINT fk_community_join_invitations_revoked_by FOREIGN KEY (revoked_by) REFERENCES users (id);

-- Token lookup during preview and registration is always by hash, and a collision must never
-- silently point two communities at the same QR.
ALTER TABLE community_join_invitations ADD CONSTRAINT uk_community_join_invitations_token_hash UNIQUE (token_hash);

ALTER TABLE community_join_invitations ADD CONSTRAINT chk_community_join_invitations_max_uses CHECK (max_uses IS NULL OR max_uses > 0);
ALTER TABLE community_join_invitations ADD CONSTRAINT chk_community_join_invitations_used_count CHECK (used_count >= 0);
ALTER TABLE community_join_invitations ADD CONSTRAINT chk_community_join_invitations_usage CHECK (max_uses IS NULL OR used_count <= max_uses);

-- Admin listing: invitations of one community, newest first.
CREATE INDEX idx_community_join_invitations_community_created
    ON community_join_invitations(community_id, created_at DESC, id DESC);

-- Lifecycle filtering in the admin rollout UI.
CREATE INDEX idx_community_join_invitations_community_status
    ON community_join_invitations(community_id, status);

-- Expiration sweeps over invitations that are still stored as ACTIVE.
CREATE INDEX idx_community_join_invitations_active_expiration
    ON community_join_invitations(expires_at)
    WHERE status = 'ACTIVE';
