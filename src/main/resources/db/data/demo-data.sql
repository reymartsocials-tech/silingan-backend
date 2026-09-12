-- =====================================================================
-- Demo data (LOCAL PROFILE ONLY)
-- ---------------------------------------------------------------------
-- Sample tenants, communities, users and issues for local development and
-- manual testing.
--
-- This file is only listed in spring.sql.init.data-locations under the
-- `local` profile document in application.yaml. The dev and prod profiles
-- load no seed data at all, so these fake tenants and the synthetic
-- `kc-*` Keycloak user IDs cannot reach a shared environment.
--
-- Every statement ends in ON CONFLICT DO NOTHING because sql.init runs on
-- every start; re-running must be a no-op and must never clobber data a
-- developer has changed locally.
-- =====================================================================

INSERT INTO addresses (
    id, street, barangay, city, province, region, postal_code, country, tower, unit_number, floor, latitude, longitude,created_at, updated_at, deleted
) VALUES
    ('10000000-0000-0000-0000-000000000001', '123 Mabini Street', 'San Isidro', 'Quezon City', 'Metro Manila', 'NCR', '1100', 'Philippines', NULL, NULL, NULL,12.0435,11.3533,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
    ('10000000-0000-0000-0000-000000000002', '45 Katipunan Avenue', 'Bagumbayan', 'Quezon City', 'Metro Manila', 'NCR', '1110', 'Philippines', 'Tower A', '1203', '12',98.3423,45.5353,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO tenants (
    id, name, contact_person, contact_email, contact_number, status, address_id, created_at, updated_at, deleted
) VALUES
    ('70000000-0000-0000-0000-000000000001', 'San Isidro LGU', 'Juan Dela Cruz', 'sanisidro@example.com', '09171234567', 'ACTIVE', '10000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('70000000-0000-0000-0000-000000000002', 'Tower A Property Management', 'Maria Santos', 'towera@example.com', '09179876543', 'ACTIVE', '10000000-0000-0000-0000-000000000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO communities (
    id, community_code, system_gen_code, name, type, address_id, status, tenant_id, created_at, updated_at, deleted
) VALUES
    ('20000000-0000-0000-0000-000000000001', 'BRGY-SAN-ISIDRO', 'BRGY-SAN-ISIDRO', 'Barangay San Isidro', 'BARANGAY', '10000000-0000-0000-0000-000000000001',  'ACTIVE', '70000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('20000000-0000-0000-0000-000000000002', 'CONDO-TOWER-A', 'CONDO-TOWER-A', 'Silingan Tower A', 'CONDO', '10000000-0000-0000-0000-000000000002', 'ACTIVE', '70000000-0000-0000-0000-000000000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO users (
    id, keycloak_user_id, created_at, updated_at, deleted
) VALUES
    ('30000000-0000-0000-0000-000000000001', 'kc-admin-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('30000000-0000-0000-0000-000000000002', 'kc-resident-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('30000000-0000-0000-0000-000000000003', 'kc-resident-002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('30000000-0000-0000-0000-000000000004', '9d248a49-e614-4d86-81ba-3b473fb7831e', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO user_communities (
    id, user_id, community_id, role, joined_at
) VALUES
    ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'PLATFORM_ADMIN', CURRENT_TIMESTAMP),
    ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'COMMUNITY_ADMIN', CURRENT_TIMESTAMP),
    ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002', 'RESIDENT', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO issue_categories (
    id, name, description, community_id, display_order, is_active, created_at, updated_at, deleted
) VALUES
    ('50000000-0000-0000-0000-000000000001', 'Road Repair', 'Concerns related to damaged roads and pathways', '20000000-0000-0000-0000-000000000001', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('50000000-0000-0000-0000-000000000002', 'Waste Management', 'Garbage collection and sanitation issues', '20000000-0000-0000-0000-000000000001', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('50000000-0000-0000-0000-000000000003', 'Elevator Maintenance', 'Elevator defects or service interruptions', '20000000-0000-0000-0000-000000000002', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO issues (
    id, title, description, status, community_id, reporter_id, category_id, custom_category, image_url, location, latitude, longitude, created_at, updated_at, deleted
) VALUES
    ('60000000-0000-0000-0000-000000000001', 'Pothole near covered court', 'Large pothole causing traffic and safety concerns near the covered court entrance.', 'OPEN', '20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', NULL, NULL, 'Covered Court Entrance, Barangay San Isidro', 14.6764, 121.0442, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    ('60000000-0000-0000-0000-000000000002', 'Elevator not stopping on 10th floor', 'Residents reported that Elevator 2 skips the 10th floor during peak hours.', 'IN_PROGRESS', '20000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000003', NULL, NULL, 'Tower A Elevator 2', 14.6093, 121.0221, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;




