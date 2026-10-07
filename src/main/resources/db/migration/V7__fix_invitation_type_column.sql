-- Migration: Fix invitation type column name mismatch
-- Description: Corrects the column name from 'invitation_type' to 'type' to match JPA entity
-- Previous V6 migration used 'invitation_type' but Hibernate entity expects 'type'
-- This migration ensures schema consistency
-- Date: 2026-10-05

-- Step 1: Check if 'invitation_type' exists and 'type' doesn't
-- If invitation_type exists, rename it to type
-- If only type exists, do nothing (already fixed)

DO $$
DECLARE
    has_invitation_type BOOLEAN;
    has_type BOOLEAN;
BEGIN
    -- Check if columns exist
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'invitations' AND column_name = 'invitation_type'
    ) INTO has_invitation_type;
    
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'invitations' AND column_name = 'type'
    ) INTO has_type;
    
    -- If invitation_type exists and type doesn't, rename it
    IF has_invitation_type AND NOT has_type THEN
        ALTER TABLE invitations RENAME COLUMN invitation_type TO type;
    -- If neither exists, create type column with default value
    ELSIF NOT has_invitation_type AND NOT has_type THEN
        ALTER TABLE invitations ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'STAFF';
        ALTER TABLE invitations ALTER COLUMN type DROP DEFAULT;
    -- If both exist (shouldn't happen), drop invitation_type
    ELSIF has_invitation_type AND has_type THEN
        ALTER TABLE invitations DROP COLUMN invitation_type;
    END IF;
END $$;

-- Step 2: Update any unique constraint if it references invitation_type
-- Drop old unique constraint if it exists
DO $$
BEGIN
    -- Get constraint name and drop it
    ALTER TABLE invitations 
    DROP CONSTRAINT IF EXISTS uq_inv_community_email_type;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- Step 3: Recreate unique constraint with correct column reference
ALTER TABLE invitations 
ADD CONSTRAINT uq_inv_community_email_type UNIQUE (community_id, email, type);

-- Step 4: Update indexes to use 'type' column instead of 'invitation_type'
-- Drop old indexes if they exist
DROP INDEX IF EXISTS idx_inv_community_type_status;
DROP INDEX IF EXISTS idx_inv_community_email_type;
DROP INDEX IF EXISTS idx_inv_staff_pending;
DROP INDEX IF EXISTS idx_inv_admin_pending;
DROP INDEX IF EXISTS idx_inv_email_pending;

-- Step 5: Recreate indexes with correct column reference
CREATE INDEX idx_inv_community_type_status ON invitations (community_id, type, status);
CREATE INDEX idx_inv_community_email_type ON invitations (community_id, email, type);

CREATE INDEX idx_inv_staff_pending ON invitations (community_id, status)
  WHERE type = 'STAFF' AND status = 'PENDING';

CREATE INDEX idx_inv_admin_pending ON invitations (community_id, status)
  WHERE type = 'ADMIN' AND status = 'PENDING';

CREATE INDEX idx_inv_email_pending ON invitations (community_id, LOWER(email), status)
  WHERE type = 'STAFF' AND status = 'PENDING';

-- Step 6: Ensure all invitations have a valid type
-- Set STAFF as default for any NULL values (shouldn't exist but safety measure)
UPDATE invitations SET type = 'STAFF' WHERE type IS NULL;

-- Step 7: Add NOT NULL constraint if not already present
ALTER TABLE invitations ALTER COLUMN type SET NOT NULL;
