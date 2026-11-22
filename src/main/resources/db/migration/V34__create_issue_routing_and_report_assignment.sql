-- Community-scoped category routing: one primary destination per community and category.
CREATE TABLE community_issue_routings (
    id                            UUID         NOT NULL,
    deleted                       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP,
    updated_at                    TIMESTAMP,
    created_by                    UUID,
    updated_by                    UUID,
    community_id                  UUID         NOT NULL,
    issue_category_id             UUID         NOT NULL,
    destination_type              VARCHAR(30)  NOT NULL,
    assigned_staff_membership_id  UUID,
    active                        BOOLEAN      NOT NULL DEFAULT TRUE,
    version                       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_community_issue_routings PRIMARY KEY (id)
);

ALTER TABLE community_issue_routings ADD CONSTRAINT fk_community_issue_routings_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE community_issue_routings ADD CONSTRAINT fk_community_issue_routings_category FOREIGN KEY (issue_category_id) REFERENCES issue_categories (id);
ALTER TABLE community_issue_routings ADD CONSTRAINT fk_community_issue_routings_staff_membership FOREIGN KEY (assigned_staff_membership_id) REFERENCES user_communities (id);
ALTER TABLE community_issue_routings ADD CONSTRAINT fk_community_issue_routings_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE community_issue_routings ADD CONSTRAINT fk_community_issue_routings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE community_issue_routings ADD CONSTRAINT chk_community_issue_routings_destination CHECK (
    (destination_type = 'STAFF' AND assigned_staff_membership_id IS NOT NULL)
    OR (destination_type = 'PMO_REVIEW_QUEUE' AND assigned_staff_membership_id IS NULL)
);

-- One routing row per community and category; it is updated in place, so at most one active destination exists.
-- Also serves the community + category lookup performed on every report submission.
CREATE UNIQUE INDEX uk_community_issue_routings_community_category
    ON community_issue_routings(community_id, issue_category_id)
    WHERE deleted = FALSE;

-- Staff routing-dependency checks (deactivation guard).
CREATE INDEX idx_community_issue_routings_active_staff
    ON community_issue_routings(assigned_staff_membership_id)
    WHERE active = TRUE AND deleted = FALSE;

-- Current assignment on the report. Existing reports are intentionally left untouched.
ALTER TABLE issues ADD COLUMN assigned_staff_membership_id UUID;
ALTER TABLE issues ADD COLUMN assignment_destination VARCHAR(30);
ALTER TABLE issues ADD COLUMN assignment_source VARCHAR(30);
ALTER TABLE issues ADD COLUMN assignment_status VARCHAR(30);
ALTER TABLE issues ADD COLUMN assigned_at TIMESTAMP;

ALTER TABLE issues ADD CONSTRAINT fk_issues_assigned_staff_membership FOREIGN KEY (assigned_staff_membership_id) REFERENCES user_communities (id);
ALTER TABLE issues ADD CONSTRAINT chk_issues_assignment_destination CHECK (
    assignment_destination IS NULL
    OR (assignment_destination = 'STAFF' AND assigned_staff_membership_id IS NOT NULL)
    OR (assignment_destination = 'PMO_REVIEW_QUEUE' AND assigned_staff_membership_id IS NULL)
);

-- Unresolved-report counts per staff membership.
CREATE INDEX idx_issues_assigned_staff_membership_status
    ON issues(assigned_staff_membership_id, status)
    WHERE assigned_staff_membership_id IS NOT NULL;

-- Append-only assignment history, including the resident-safe activity text.
CREATE TABLE issue_assignment_histories (
    id                             UUID          NOT NULL,
    deleted                        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMP,
    updated_at                     TIMESTAMP,
    created_by                     UUID,
    updated_by                     UUID,
    issue_id                       UUID          NOT NULL,
    previous_destination           VARCHAR(30),
    previous_staff_membership_id   UUID,
    new_destination                VARCHAR(30)   NOT NULL,
    new_staff_membership_id        UUID,
    assignment_source              VARCHAR(30)   NOT NULL,
    reason                         VARCHAR(500),
    changed_by                     UUID,
    changed_at                     TIMESTAMP     NOT NULL,
    activity_title                 VARCHAR(200)  NOT NULL,
    activity_message               VARCHAR(500)  NOT NULL,
    CONSTRAINT pk_issue_assignment_histories PRIMARY KEY (id)
);

ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_issue FOREIGN KEY (issue_id) REFERENCES issues (id);
ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_previous_staff FOREIGN KEY (previous_staff_membership_id) REFERENCES user_communities (id);
ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_new_staff FOREIGN KEY (new_staff_membership_id) REFERENCES user_communities (id);
ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_changed_by FOREIGN KEY (changed_by) REFERENCES users (id);
ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE issue_assignment_histories ADD CONSTRAINT fk_issue_assignment_histories_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE issue_assignment_histories ADD CONSTRAINT chk_issue_assignment_histories_new_staff CHECK (
    (new_destination = 'STAFF' AND new_staff_membership_id IS NOT NULL)
    OR (new_destination = 'PMO_REVIEW_QUEUE' AND new_staff_membership_id IS NULL)
);

-- Report timeline lookup.
CREATE INDEX idx_issue_assignment_histories_issue
    ON issue_assignment_histories(issue_id, changed_at, id);
