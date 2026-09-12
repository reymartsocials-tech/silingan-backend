
CREATE TABLE user_community_staff_roles (
											id           UUID        NOT NULL,
											active       BOOLEAN     NOT NULL,
											assigned_at  TIMESTAMP   NOT NULL,
											updated_at   TIMESTAMP,
											assigned_by  UUID,
											community_id UUID,
											user_id      UUID,
											role_code    VARCHAR(64) NOT NULL,
											CONSTRAINT pk_ucsr PRIMARY KEY (id),
											CONSTRAINT uk_ucsr_user_community UNIQUE (user_id, community_id)
);

CREATE INDEX idx_ucsr_user_community ON user_community_staff_roles (user_id, community_id);
CREATE INDEX idx_ucsr_community_role ON user_community_staff_roles (community_id, role_code);

-- Foreign keys
ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id);
ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_user FOREIGN KEY (user_id) REFERENCES users (id);