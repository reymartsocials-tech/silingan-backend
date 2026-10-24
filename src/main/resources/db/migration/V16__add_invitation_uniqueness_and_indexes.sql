-- Add partial unique index for PENDING invitations by unique key (community_id, email, role_code, type)
-- This prevents concurrent duplicate pending invitations for the same email/roleCode in a community
-- EXPIRED, ACCEPTED, REVOKED statuses are allowed to coexist
CREATE UNIQUE INDEX idx_inv_unique_pending_by_email_role
ON invitations(community_id, email, role_code, type)
WHERE status = 'PENDING' AND role_code IS NOT NULL;

-- Add composite index for idempotent invite checks
-- Supports fast lookup of recent invitations by email+role regardless of status
CREATE INDEX idx_inv_email_role_status
ON invitations(community_id, type, email, role_code, status DESC, invited_at DESC);

-- Add index for revoke operation lookups
CREATE INDEX idx_inv_keycloak_user_status
ON invitations(keycloak_user_id, status, type)
WHERE keycloak_user_id IS NOT NULL;

-- Add index for statistics queries
CREATE INDEX idx_inv_community_status
ON invitations(community_id, status);
