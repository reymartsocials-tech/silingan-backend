-- Cleanup by Keycloak User ID
DELETE FROM invitations WHERE keycloak_user_id = 'keycloak-user-id-abc123';
DELETE FROM user_communities WHERE user_id IN (SELECT id FROM users WHERE keycloak_user_id = 'keycloak-user-id-abc123');
DELETE FROM users WHERE keycloak_user_id = 'keycloak-user-id-abc123';
