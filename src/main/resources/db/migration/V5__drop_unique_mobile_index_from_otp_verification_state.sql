-- Allow multiple OTP verification state rows per mobile number.
DROP INDEX IF EXISTS idx_otp_state_mobile;
