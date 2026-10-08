-- Migration: Drop active and status columns from user_communities
-- Description:
--   Removes redundant 'active' and 'status' columns from user_communities table.
--   The user_community status is tied to the user status, so we can derive it
--   by joining with the users table instead of storing it separately.
--   This eliminates data duplication and simplifies the schema.
--   Queries now use users.status instead of user_communities.status.
-- Date: 2026-10-07

-- Step 1: Drop the old composite index that includes active
DROP INDEX IF EXISTS idx_user_communities_user_community_active;

-- Step 2: Drop the status-based indexes
DROP INDEX IF EXISTS idx_user_communities_status;
DROP INDEX IF EXISTS idx_user_communities_user_status;
DROP INDEX IF EXISTS idx_user_communities_community_status;

-- Step 3: Drop both active and status columns
ALTER TABLE user_communities
    DROP COLUMN active;

ALTER TABLE user_communities
    DROP COLUMN status;
