-- Migration: Add staff_invitations table
-- Description: Creates the staff_invitations table for staff member invitations
-- Version: V4
-- Date: 2026-09-19

CREATE TABLE IF NOT EXISTS staff_invitations (
    id UUID PRIMARY KEY,
    community_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(20),
    position VARCHAR(255) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    invitation_token VARCHAR(512) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    invited_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    invited_by UUID,
    notes TEXT,
    CONSTRAINT fk_si_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE CASCADE,
    CONSTRAINT fk_si_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_si_community_email_status UNIQUE (community_id, email, status)
);

-- Create indexes for query performance
CREATE INDEX IF NOT EXISTS idx_si_community_status ON staff_invitations (community_id, status);
CREATE INDEX IF NOT EXISTS idx_si_community_email ON staff_invitations (community_id, email);
CREATE INDEX IF NOT EXISTS idx_si_community_mobile ON staff_invitations (community_id, mobile_number);
CREATE INDEX IF NOT EXISTS idx_si_token ON staff_invitations (invitation_token);
CREATE INDEX IF NOT EXISTS idx_si_expires_at ON staff_invitations (expires_at);
