CREATE TABLE notifications (
    id                 UUID          NOT NULL,
    deleted            BOOLEAN       NOT NULL,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    created_by         UUID,
    updated_by         UUID,
    recipient_user_id  UUID          NOT NULL,
    community_id       UUID,
    notification_type  VARCHAR(100)  NOT NULL,
    category           VARCHAR(50)   NOT NULL,
    priority           VARCHAR(30)   NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    body               VARCHAR(2000) NOT NULL,
    reference_type     VARCHAR(50),
    reference_id       VARCHAR(100),
    action_path        VARCHAR(500),
    source_event_id    VARCHAR(200),
    read_at            TIMESTAMP,
    expires_at         TIMESTAMP,
    archived_at        TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_recipient_user FOREIGN KEY (recipient_user_id) REFERENCES users (id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_community FOREIGN KEY (community_id) REFERENCES communities (id);

-- Inbox query: recipient + optional community, newest first with deterministic tie-breaker.
CREATE INDEX idx_notifications_inbox
    ON notifications(recipient_user_id, community_id, created_at DESC, id DESC);

-- Unread lookups and counts in community scope.
CREATE INDEX idx_notifications_unread
    ON notifications(recipient_user_id, community_id, read_at, created_at DESC);

-- Fast unread-only inbox when supported by PostgreSQL.
CREATE INDEX idx_notifications_unread_active
    ON notifications(recipient_user_id, community_id, created_at DESC, id DESC)
    WHERE read_at IS NULL AND archived_at IS NULL;

-- Reference-based diagnostics and future source-level operations.
CREATE INDEX idx_notifications_reference_lookup
    ON notifications(reference_type, reference_id, recipient_user_id);

-- Idempotent event replay per recipient.
CREATE UNIQUE INDEX uk_notifications_recipient_source_event
    ON notifications(recipient_user_id, source_event_id)
    WHERE source_event_id IS NOT NULL;
