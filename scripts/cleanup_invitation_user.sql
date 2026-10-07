-- Cleanup by Email
-- Replace 'test@example.com' with actual email

DELETE FROM invitations WHERE email = 'test@example.com';
DELETE FROM user_communities WHERE user_id IN (SELECT id FROM users WHERE email = 'test@example.com');
DELETE FROM users WHERE email = 'test@example.com';
