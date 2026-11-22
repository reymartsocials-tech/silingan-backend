CREATE TABLE issue_resolution_decisions (
    id          UUID         NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    issue_id    UUID         NOT NULL,
    resident_id UUID         NOT NULL,
    decision    VARCHAR(30)  NOT NULL,
    decided_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_issue_resolution_decisions PRIMARY KEY (id),
    CONSTRAINT fk_issue_resolution_decisions_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_resolution_decisions_resident FOREIGN KEY (resident_id) REFERENCES users (id),
    CONSTRAINT fk_issue_resolution_decisions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_issue_resolution_decisions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT chk_issue_resolution_decisions_decision CHECK (
        decision IN ('CONFIRMED_RESOLVED', 'STILL_AN_ISSUE')
    )
);

CREATE INDEX idx_issue_resolution_decisions_issue
    ON issue_resolution_decisions(issue_id, decided_at, id);
