-- Remove imageUrl field from issues table (use attachments instead)
ALTER TABLE issues DROP COLUMN IF EXISTS image_url;

-- Add urgency_level field
ALTER TABLE issues ADD COLUMN urgency_level VARCHAR(50);

-- Add assigned_to_user_id field (FK to users)
ALTER TABLE issues ADD COLUMN assigned_to_user_id UUID;
ALTER TABLE issues ADD CONSTRAINT fk_issues_assigned_to_user_id 
    FOREIGN KEY (assigned_to_user_id) REFERENCES users(id);

-- Add resolved_at field (when issue was resolved/closed)
ALTER TABLE issues ADD COLUMN resolved_at TIMESTAMP;

-- Add resolution_notes field (notes on how the issue was resolved, max 2000 chars)
ALTER TABLE issues ADD COLUMN resolution_notes VARCHAR(2000);
