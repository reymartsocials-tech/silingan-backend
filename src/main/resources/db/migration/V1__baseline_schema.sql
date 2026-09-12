-- =====================================================================
-- V1 - Baseline schema
-- ---------------------------------------------------------------------
-- Derived from the JPA model as of the introduction of Flyway. From this
-- point on Hibernate runs with ddl-auto=validate: it never changes the
-- schema, it only asserts that the schema matches the entities. Every
-- structural change must arrive as a new versioned migration.
--
-- Deliberate deviations from the Hibernate-generated DDL:
--   * All constraints and foreign keys are explicitly named so that later
--     migrations (and error messages) can refer to them.
--   * users.email / users.mobile_number use PARTIAL unique indexes scoped
--     to WHERE deleted = false. A full-table unique constraint would make
--     a soft-deleted user's email permanently unusable.
--   * Enum columns are plain varchar without CHECK constraints. The Java
--     enums are the source of truth; baking the value list into the schema
--     means every new enum constant needs a migration for no added safety.
--
-- SCOPE: this directory contains SCHEMA ONLY. Seed and reference data are
-- applied separately from db/data by spring.sql.init after migration, so
-- that they can be re-asserted on every boot instead of running exactly once.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Core reference tables (no dependencies)
-- ---------------------------------------------------------------------

CREATE TABLE addresses (
    id            UUID             NOT NULL,
    deleted       BOOLEAN          NOT NULL,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    created_by    UUID,
    updated_by    UUID,
    barangay      VARCHAR(255),
    building_name VARCHAR(255),
    city          VARCHAR(255),
    country       VARCHAR(255),
    floor         VARCHAR(255),
    postal_code   VARCHAR(255),
    province      VARCHAR(255),
    region        VARCHAR(255),
    street        VARCHAR(255),
    tower         VARCHAR(255),
    unit_number   VARCHAR(255),
    CONSTRAINT pk_addresses PRIMARY KEY (id)
);

CREATE TABLE users (
    id               UUID         NOT NULL,
    deleted          BOOLEAN      NOT NULL,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    created_by       UUID,
    updated_by       UUID,
    keycloak_user_id VARCHAR(255) NOT NULL,
    username         VARCHAR(255),
    first_name       VARCHAR(255),
    last_name        VARCHAR(255),
    email            VARCHAR(255),
    mobile_number    VARCHAR(20),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_keycloak_user_id UNIQUE (keycloak_user_id)
);

CREATE INDEX idx_user_keycloak_id ON users (keycloak_user_id);

-- Identity uniqueness, scoped to live rows only.
-- Normalisation (lowercase email, +63 mobile) happens in ContactNormalizer
-- and in User's @PrePersist/@PreUpdate, so a plain index is sufficient here.
CREATE UNIQUE INDEX uk_users_email_active
    ON users (email)
    WHERE deleted = FALSE AND email IS NOT NULL;

CREATE UNIQUE INDEX uk_users_mobile_number_active
    ON users (mobile_number)
    WHERE deleted = FALSE AND mobile_number IS NOT NULL;

CREATE TABLE tenants (
    id                UUID         NOT NULL,
    deleted           BOOLEAN      NOT NULL,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    created_by        UUID,
    updated_by        UUID,
    address_id        UUID,
    name              VARCHAR(255) NOT NULL,
    status            VARCHAR(255) NOT NULL,
    contact_email     VARCHAR(255),
    contact_number    VARCHAR(255),
    contact_person    VARCHAR(255),
    subscription_plan VARCHAR(255),
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_address UNIQUE (address_id)
);

CREATE TABLE media (
    id             UUID         NOT NULL,
    deleted        BOOLEAN      NOT NULL,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    community_id   UUID,
    reference_id   UUID         NOT NULL,
    reference_type VARCHAR(255) NOT NULL,
    media_type     VARCHAR(255) NOT NULL,
    content_type   VARCHAR(255) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    stored_name    VARCHAR(255) NOT NULL,
    url            VARCHAR(255) NOT NULL,
    CONSTRAINT pk_media PRIMARY KEY (id)
);

CREATE TABLE communities (
    id              UUID         NOT NULL,
    deleted         BOOLEAN      NOT NULL,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    address_id      UUID,
    logo_id         UUID,
    tenant_id       UUID,
    community_code  VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(255),
    system_gen_code VARCHAR(255),
    type            VARCHAR(255),
    CONSTRAINT pk_communities PRIMARY KEY (id),
    CONSTRAINT uk_communities_community_code UNIQUE (community_code),
    CONSTRAINT uk_communities_address UNIQUE (address_id),
    CONSTRAINT uk_communities_logo UNIQUE (logo_id),
    CONSTRAINT uk_tenant_community UNIQUE (tenant_id, community_code)
);

CREATE INDEX idx_community_code ON communities (community_code);
CREATE INDEX idx_tenant_community ON communities (tenant_id, community_code);

-- ---------------------------------------------------------------------
-- RBAC: staff role assignment
-- ---------------------------------------------------------------------
-- The role catalog and the permission matrix are defined in code
-- (StaffRoleCatalog) and are read-only for MVP: there are no custom roles,
-- custom permissions, or per-community overrides, so nothing about them is
-- stored here. Only the user -> role binding is persisted.

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

-- Community membership (the tenant boundary CommunityScopeGuard reads).
CREATE TABLE user_communities (
    id           UUID         NOT NULL,
    joined_at    TIMESTAMP,
    community_id UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    role         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_communities PRIMARY KEY (id),
    CONSTRAINT uk_user_communities_user_community UNIQUE (user_id, community_id)
);

CREATE TABLE community_admin_invitations (
    id               UUID         NOT NULL,
    community_id     UUID         NOT NULL,
    invited_at       TIMESTAMP(6) NOT NULL,
    accepted_at      TIMESTAMP(6),
    email            VARCHAR(255) NOT NULL,
    keycloak_user_id VARCHAR(255) NOT NULL,
    status           VARCHAR(255) NOT NULL,
    CONSTRAINT pk_community_admin_invitations PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- Domain tables
-- ---------------------------------------------------------------------

CREATE TABLE announcements (
    id           UUID         NOT NULL,
    deleted      BOOLEAN      NOT NULL,
    is_pinned    BOOLEAN      NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,
    community_id UUID,
    publish_at   TIMESTAMP(6) WITH TIME ZONE,
    category     VARCHAR(32)  NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    CONSTRAINT pk_announcements PRIMARY KEY (id)
);

CREATE INDEX idx_title_community ON announcements (title, community_id);

CREATE TABLE issue_categories (
    id            UUID         NOT NULL,
    deleted       BOOLEAN      NOT NULL,
    display_order INTEGER,
    is_active     BOOLEAN,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    created_by    UUID,
    updated_by    UUID,
    community_id  UUID,
    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(255),
    CONSTRAINT pk_issue_categories PRIMARY KEY (id)
);

CREATE TABLE issues (
    id              UUID             NOT NULL,
    deleted         BOOLEAN          NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    category_id     UUID             NOT NULL,
    community_id    UUID,
    reporter_id     UUID             NOT NULL,
    custom_category VARCHAR(255),
    description     TEXT             NOT NULL,
    image_url       VARCHAR(255),
    location        VARCHAR(255),
    status          VARCHAR(255)     NOT NULL,
    title           VARCHAR(255)     NOT NULL,
    CONSTRAINT pk_issues PRIMARY KEY (id)
);

CREATE TABLE otp_verification_state (
    id               UUID         NOT NULL,
    otp_verified     BOOLEAN      NOT NULL,
    expires_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    verified_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    keycloak_user_id VARCHAR(128) NOT NULL,
    token_id         VARCHAR(256),
    CONSTRAINT pk_otp_verification_state PRIMARY KEY (id)
);

CREATE INDEX idx_otp_state_user ON otp_verification_state (keycloak_user_id);
CREATE INDEX idx_otp_state_exp ON otp_verification_state (expires_at);
CREATE INDEX idx_otp_state_user_token ON otp_verification_state (keycloak_user_id, token_id);

-- ---------------------------------------------------------------------
-- Foreign keys (declared last so table order above does not matter)
-- ---------------------------------------------------------------------

ALTER TABLE addresses ADD CONSTRAINT fk_addresses_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE addresses ADD CONSTRAINT fk_addresses_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE users ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE users ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE tenants ADD CONSTRAINT fk_tenants_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_address FOREIGN KEY (address_id) REFERENCES addresses (id);

ALTER TABLE media ADD CONSTRAINT fk_media_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE media ADD CONSTRAINT fk_media_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE media ADD CONSTRAINT fk_media_community FOREIGN KEY (community_id) REFERENCES communities (id);

ALTER TABLE communities ADD CONSTRAINT fk_communities_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE communities ADD CONSTRAINT fk_communities_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE communities ADD CONSTRAINT fk_communities_address FOREIGN KEY (address_id) REFERENCES addresses (id);
ALTER TABLE communities ADD CONSTRAINT fk_communities_logo FOREIGN KEY (logo_id) REFERENCES media (id);
ALTER TABLE communities ADD CONSTRAINT fk_communities_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id);
ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE user_community_staff_roles ADD CONSTRAINT fk_ucsr_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_communities ADD CONSTRAINT fk_user_communities_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE user_communities ADD CONSTRAINT fk_user_communities_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE community_admin_invitations ADD CONSTRAINT fk_cai_community FOREIGN KEY (community_id) REFERENCES communities (id);

ALTER TABLE announcements ADD CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_community FOREIGN KEY (community_id) REFERENCES communities (id);

ALTER TABLE issue_categories ADD CONSTRAINT fk_issue_categories_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE issue_categories ADD CONSTRAINT fk_issue_categories_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE issue_categories ADD CONSTRAINT fk_issue_categories_community FOREIGN KEY (community_id) REFERENCES communities (id);

ALTER TABLE issues ADD CONSTRAINT fk_issues_created_by FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE issues ADD CONSTRAINT fk_issues_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE issues ADD CONSTRAINT fk_issues_category FOREIGN KEY (category_id) REFERENCES issue_categories (id);
ALTER TABLE issues ADD CONSTRAINT fk_issues_community FOREIGN KEY (community_id) REFERENCES communities (id);
ALTER TABLE issues ADD CONSTRAINT fk_issues_reporter FOREIGN KEY (reporter_id) REFERENCES users (id);
