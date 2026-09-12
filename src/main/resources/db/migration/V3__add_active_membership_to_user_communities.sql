ALTER TABLE user_communities
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE user_communities
SET active = TRUE
WHERE active IS NULL;

ALTER TABLE user_communities
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE user_communities
    ALTER COLUMN active SET DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_user_communities_user_community_active
    ON user_communities (user_id, community_id, active);
