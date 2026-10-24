-- Resident feed access pattern: filter by community and PUBLISHED status, ordered by publication
-- date descending. The existing (community_id, status) and (community_id, published_at) indexes
-- each cover only half of that, forcing a sort or a heap filter.
CREATE INDEX idx_announcements_community_status_published_at
	ON announcements(community_id, status, published_at DESC);
