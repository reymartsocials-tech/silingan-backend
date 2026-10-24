-- Create attachments table to store file metadata
CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path TEXT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_attachment_created_at ON attachments(created_at);
CREATE INDEX idx_attachment_file_name ON attachments(file_name);
