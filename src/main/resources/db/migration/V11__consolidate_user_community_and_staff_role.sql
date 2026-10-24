-- Consolidate UserCommunity and UserCommunityStaffRole tables
-- Migrate staff role data into user_communities and remove duplicate table

-- Step 1: Add new columns to user_communities table for staff role data
ALTER TABLE user_communities 
ADD COLUMN role_code VARCHAR(64),
ADD COLUMN assigned_at TIMESTAMP,
ADD COLUMN assigned_by UUID,
ADD COLUMN updated_at TIMESTAMP;

-- Step 2: Migrate data from user_community_staff_roles to user_communities
UPDATE user_communities uc
SET 
  role_code = ucsr.role_code,
  assigned_at = ucsr.assigned_at,
  assigned_by = ucsr.assigned_by,
  updated_at = ucsr.updated_at
FROM user_community_staff_roles ucsr
WHERE uc.user_id = ucsr.user_id 
  AND uc.community_id = ucsr.community_id;

-- Step 3: Drop the old user_community_staff_roles table
DROP TABLE IF EXISTS user_community_staff_roles CASCADE;

-- Step 4: Add indexes on user_communities for staff role queries
CREATE INDEX idx_uc_community_staff_role ON user_communities(community_id, role_code)
WHERE role_code IS NOT NULL;
