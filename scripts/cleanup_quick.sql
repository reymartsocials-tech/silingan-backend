-- Quick cleanup by email
DELETE FROM invitations WHERE email = 'cabarubias05@gmail.com';
DELETE FROM user_communities WHERE user_id IN (SELECT id FROM users WHERE email = 'cabarubias05@gmail.com');
DELETE FROM users WHERE email = 'cabarubias05@gmail.com';


DELETE FROM invitations WHERE email = 'reymart.castillon051798@gmail.com';
DELETE FROM user_communities WHERE user_id IN (SELECT id FROM users WHERE email = 'reymart.castillon051798@gmail.com');
DELETE FROM users WHERE email = 'reymart.castillon051798@gmail.com';
