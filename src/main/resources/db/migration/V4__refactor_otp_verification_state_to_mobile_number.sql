-- Refactor OTP verification state to mobile-number-based verification (no keycloak id, no token id, no user id).
ALTER TABLE otp_verification_state
    ADD COLUMN mobile_number VARCHAR(20);

UPDATE otp_verification_state ovs
SET mobile_number = u.mobile_number
FROM users u
WHERE u.keycloak_user_id = ovs.keycloak_user_id;

DELETE FROM otp_verification_state
WHERE mobile_number IS NULL OR mobile_number = '';

DROP INDEX IF EXISTS idx_otp_state_user_token;
DROP INDEX IF EXISTS idx_otp_state_user;

ALTER TABLE otp_verification_state
    ALTER COLUMN mobile_number SET NOT NULL;

CREATE UNIQUE INDEX idx_otp_state_mobile ON otp_verification_state (mobile_number);

ALTER TABLE otp_verification_state
    DROP COLUMN keycloak_user_id,
    DROP COLUMN token_id;
