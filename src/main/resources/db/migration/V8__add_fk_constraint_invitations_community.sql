-- Migration: Add Foreign Key constraint for invitations.community_id and cleanup staff-specific fields
-- Description: 
--   1. Ensures referential integrity between invitations and communities
--   2. Drops staff-specific fields (firstName, lastName, mobileNumber, position) 
--      since these are now fetched from Keycloak during user creation
--   3. Cleans up unused indexes
-- Date: 2026-10-05

-- Step 1: Drop staff-specific columns (these fields are in User entity, not Invitation)
ALTER TABLE invitations 
DROP COLUMN IF EXISTS first_name,
DROP COLUMN IF EXISTS last_name,
DROP COLUMN IF EXISTS mobile_number,
DROP COLUMN IF EXISTS position;

-- Step 2: Clean up old unused indexes
DROP INDEX IF EXISTS idx_inv_token;
DROP INDEX IF EXISTS idx_inv_staff_pending;
DROP INDEX IF EXISTS idx_inv_admin_pending;
DROP INDEX IF EXISTS idx_inv_email_pending;
DROP INDEX IF EXISTS idx_inv_community_email_type;

-- Step 3: Check if FK already exists, if not add it
DO $$
DECLARE
    constraint_exists BOOLEAN;
BEGIN
    -- Check if the foreign key constraint exists
    SELECT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'invitations' 
        AND constraint_name = 'fk_inv_community'
        AND constraint_type = 'FOREIGN KEY'
    ) INTO constraint_exists;
    
    -- If it doesn't exist, add it
    IF NOT constraint_exists THEN
        ALTER TABLE invitations
        ADD CONSTRAINT fk_inv_community 
        FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE CASCADE;
        
        RAISE NOTICE 'Added Foreign Key constraint fk_inv_community to invitations table';
    ELSE
        RAISE NOTICE 'Foreign Key constraint fk_inv_community already exists';
    END IF;
END $$;

-- Step 4: Ensure community_id is NOT NULL in invitations
ALTER TABLE invitations 
ALTER COLUMN community_id SET NOT NULL;

-- Step 5: Recreate necessary indexes only
CREATE INDEX IF NOT EXISTS idx_inv_community_type_status ON invitations (community_id, type, status);
CREATE INDEX IF NOT EXISTS idx_inv_community_email ON invitations (community_id, email);
CREATE INDEX IF NOT EXISTS idx_inv_expires_at ON invitations (expires_at);
