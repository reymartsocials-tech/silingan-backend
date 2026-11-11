-- Announcements record who published them and when, so the DRAFT -> PUBLISHED transition is
-- auditable and repeated publish requests stay idempotent.
ALTER TABLE announcements ADD COLUMN published_at TIMESTAMP;
ALTER TABLE announcements ADD COLUMN published_by UUID;

ALTER TABLE announcements ADD CONSTRAINT fk_announcements_published_by FOREIGN KEY (published_by) REFERENCES users (id);

-- Existing published rows predate the column; fall back to their last known timestamp.
UPDATE announcements
SET published_at = COALESCE(updated_at, created_at)
WHERE status = 'PUBLISHED'
  AND published_at IS NULL;

-- Every announcement belongs to exactly one community.
ALTER TABLE announcements ALTER COLUMN community_id SET NOT NULL;

CREATE INDEX idx_announcements_community_published_at
	ON announcements(community_id, published_at DESC);
