-- Migration: Add status columns for onboarding persistence
-- Description: 
--   Implements invitation onboarding persistence by creating user and membership
--   records during invitation creation (PENDING status) instead of waiting for first login.
--   On first login, records are activated (status → ACTIVE).
-- Date: 2026-10-05

-- Step 1: Add status column to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'PENDING' NOT NULL;

-- Step 2: Add status column to user_communities table
ALTER TABLE user_communities
ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'PENDING' NOT NULL;

-- Step 3: Set existing active users to ACTIVE (backward compatibility)
UPDATE users SET status = 'ACTIVE' WHERE status = 'PENDING';

-- Step 4: Set existing active memberships to ACTIVE (backward compatibility)
UPDATE user_communities SET status = 'ACTIVE' WHERE active = true AND status = 'PENDING';

-- Step 5: Set inactive memberships to INACTIVE (backward compatibility)
UPDATE user_communities SET status = 'INACTIVE' WHERE active = false AND status = 'PENDING';

-- Step 6: Add indexes for efficient status lookups
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_user_communities_status ON user_communities (status);
CREATE INDEX IF NOT EXISTS idx_user_communities_user_status ON user_communities (user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_communities_community_status ON user_communities (community_id, status);
