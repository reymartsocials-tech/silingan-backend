-- Create attachment_references table for polymorphic attachment ownership
CREATE TABLE attachment_references (
    id UUID PRIMARY KEY,
    attachment_id UUID NOT NULL REFERENCES attachments(id),
    owner_type VARCHAR(32) NOT NULL,
    owner_id UUID NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_attachment_owner UNIQUE (attachment_id, owner_type, owner_id, display_order)
);

CREATE INDEX idx_attachment_reference_owner ON attachment_references(owner_type, owner_id);
CREATE INDEX idx_attachment_reference_attachment ON attachment_references(attachment_id);

-- Add profile picture support to user_communities table (per-community user profile pictures)
ALTER TABLE user_communities 
ADD COLUMN profile_picture_attachment_id UUID REFERENCES attachments(id);

CREATE INDEX idx_user_community_profile_picture ON user_communities(profile_picture_attachment_id);

-- Add logo support to communities table
ALTER TABLE communities 
ADD COLUMN logo_attachment_id UUID REFERENCES attachments(id);

CREATE INDEX idx_community_logo ON communities(logo_attachment_id);
