-- Migration: Consolidate staff and admin invitations into unified table
-- Description: Creates unified invitations table supporting both staff and admin invitation types
-- This enables shared logic at the service layer and simplifies the schema
-- Version: V9
-- Date: 2026-10-04

-- Create unified invitations table
CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY,
    invitation_type VARCHAR(32) NOT NULL, -- ENUM: STAFF, ADMIN
    community_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    
    -- Admin-specific (nullable for staff)
    keycloak_user_id VARCHAR(255),
    
    -- Staff-specific (nullable for admin)
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    mobile_number VARCHAR(20),
    position VARCHAR(255),
    role_code VARCHAR(64),
    invited_by UUID,
    notes TEXT,
    
    -- Common fields
    status VARCHAR(32) NOT NULL,
    invited_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    
    CONSTRAINT fk_inv_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_inv_community_email_type UNIQUE (community_id, email, invitation_type)
);

-- Indexes for query performance
-- Type-aware community queries
CREATE INDEX IF NOT EXISTS idx_inv_community_type_status ON invitations (community_id, invitation_type, status);

-- Email lookups
CREATE INDEX IF NOT EXISTS idx_inv_community_email_type ON invitations (community_id, email, invitation_type);

-- Expiration checks
CREATE INDEX IF NOT EXISTS idx_inv_expires_at ON invitations (expires_at);

-- Partial indexes for active staff/admin invitations (common query pattern)
CREATE INDEX IF NOT EXISTS idx_inv_staff_pending ON invitations (community_id, status)
  WHERE invitation_type = 'STAFF' AND status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_inv_admin_pending ON invitations (community_id, status)
  WHERE invitation_type = 'ADMIN' AND status = 'PENDING';

-- Partial index for email lookups on pending invitations
CREATE INDEX IF NOT EXISTS idx_inv_email_pending ON invitations (community_id, LOWER(email), status)
  WHERE invitation_type = 'STAFF' AND status = 'PENDING';


-- Drop old tables
DROP TABLE IF EXISTS staff_invitations;
DROP TABLE IF EXISTS community_admin_invitations;
