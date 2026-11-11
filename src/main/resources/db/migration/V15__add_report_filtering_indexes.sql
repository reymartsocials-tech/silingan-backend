-- issue indexes for filtering issues and announcements by community, status, category, assigned_to_user_id, created_at, updated_at, and publish_at
CREATE INDEX idx_issues_community_status
	ON issues(community_id, status);

CREATE INDEX idx_issues_community_category
	ON issues(community_id, category_id);

CREATE INDEX idx_issues_assigned_to_community
	ON issues(assigned_to_user_id, community_id);

CREATE INDEX idx_issues_community_created_at
	ON issues(community_id, created_at DESC);

CREATE INDEX idx_issues_community_updated_at
	ON issues(community_id, updated_at DESC);

-- announcement indexes for filtering announcements by community, status, category, publish_at, and created_at
CREATE INDEX idx_announcements_community_status
	ON announcements(community_id, status);

CREATE INDEX idx_announcements_community_category
	ON announcements(community_id, category);

CREATE INDEX idx_announcements_community_publish_at
	ON announcements(community_id, publish_at DESC);

CREATE INDEX idx_announcements_community_created_at
	ON announcements(community_id, created_at DESC);
