-- Resident interactions with published announcements. Residents are referenced through their
-- community membership so the community context stays on the record.
-- The unique constraints are the authority for "once per resident and announcement" and are what
-- makes concurrent duplicate requests safe; they also serve the per-announcement count queries.
CREATE TABLE announcement_acknowledgements (
    id                UUID      NOT NULL,
    announcement_id   UUID      NOT NULL,
    user_community_id UUID      NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    CONSTRAINT pk_announcement_acknowledgements PRIMARY KEY (id),
    CONSTRAINT uk_ann_ack_announcement_member UNIQUE (announcement_id, user_community_id)
);

ALTER TABLE announcement_acknowledgements
    ADD CONSTRAINT fk_ann_ack_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id);
ALTER TABLE announcement_acknowledgements
    ADD CONSTRAINT fk_ann_ack_user_community FOREIGN KEY (user_community_id) REFERENCES user_communities (id);

-- Serves "which of these announcements has this resident already acknowledged".
CREATE INDEX idx_ann_ack_member_announcement
    ON announcement_acknowledgements (user_community_id, announcement_id);

CREATE TABLE announcement_helpful_marks (
    id                UUID      NOT NULL,
    announcement_id   UUID      NOT NULL,
    user_community_id UUID      NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    CONSTRAINT pk_announcement_helpful_marks PRIMARY KEY (id),
    CONSTRAINT uk_ann_helpful_announcement_member UNIQUE (announcement_id, user_community_id)
);

ALTER TABLE announcement_helpful_marks
    ADD CONSTRAINT fk_ann_helpful_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id);
ALTER TABLE announcement_helpful_marks
    ADD CONSTRAINT fk_ann_helpful_user_community FOREIGN KEY (user_community_id) REFERENCES user_communities (id);

CREATE INDEX idx_ann_helpful_member_announcement
    ON announcement_helpful_marks (user_community_id, announcement_id);
