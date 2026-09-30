package com.ria.olita.tech.silingan.service.otp;

import java.time.Instant;

public interface OtpVerificationStateService {

	boolean isVerified(String mobileNumber);

	void markVerified(String mobileNumber, Instant expiresAt);

	void clearVerification(String mobileNumber);
}
