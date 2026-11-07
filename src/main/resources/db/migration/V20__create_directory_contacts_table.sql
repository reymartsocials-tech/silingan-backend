-- Community directory and emergency contacts. Each contact belongs to exactly one community;
-- is_active controls resident visibility while deleted keeps the existing soft-delete convention.
CREATE TABLE directory_contacts (
    id                     UUID         NOT NULL,
    community_id           UUID         NOT NULL,
    category               VARCHAR(32)  NOT NULL,
    contact_type           VARCHAR(48)  NOT NULL,
    display_name           VARCHAR(150) NOT NULL,
    primary_phone_number   VARCHAR(32)  NOT NULL,
    secondary_phone_number VARCHAR(32),
    description            VARCHAR(500),
    operating_hours        VARCHAR(150),
    address                VARCHAR(255),
    display_order          INTEGER      NOT NULL DEFAULT 0,
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    created_by             UUID,
    updated_by             UUID,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_directory_contacts PRIMARY KEY (id),
    CONSTRAINT chk_directory_contact_display_order CHECK (display_order >= 0)
);

ALTER TABLE directory_contacts
    ADD CONSTRAINT fk_directory_contact_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE directory_contacts
    ADD CONSTRAINT fk_directory_contact_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE directory_contacts
    ADD CONSTRAINT fk_directory_contact_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

-- Staff listing and every community-scoped lookup.
CREATE INDEX idx_directory_contact_community
    ON directory_contacts (community_id);

-- Resident feed: community, active status, then the category and display-order sort keys.
CREATE INDEX idx_directory_contact_resident_feed
    ON directory_contacts (community_id, is_active, category, display_order);
