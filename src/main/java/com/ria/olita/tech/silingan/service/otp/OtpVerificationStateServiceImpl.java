package com.ria.olita.tech.silingan.service.otp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.entity.OtpVerificationState;
import com.ria.olita.tech.silingan.repository.OtpVerificationStateRepository;
import com.ria.olita.tech.silingan.util.ContactNormalizer;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpVerificationStateServiceImpl implements OtpVerificationStateService {

	private final OtpVerificationStateRepository otpVerificationStateRepository;

	@Override
	@Transactional(readOnly = true)
	public boolean isVerified(String mobileNumber) {
		String normalizedMobileNumber = ContactNormalizer.normalizeMobileNumber(mobileNumber);
		return otpVerificationStateRepository.existsByMobileNumberAndOtpVerifiedTrueAndExpiresAtAfter(
			normalizedMobileNumber,
			Instant.now()
		);
	}

	@Override
	@Transactional
	public void markVerified(String mobileNumber, Instant expiresAt) {
		String normalizedMobileNumber = ContactNormalizer.normalizeMobileNumber(mobileNumber);
		clearVerification(normalizedMobileNumber);

		OtpVerificationState state = OtpVerificationState.builder()
			.mobileNumber(normalizedMobileNumber)
			.otpVerified(true)
			.verifiedAt(Instant.now())
			.expiresAt(expiresAt)
			.build();

		otpVerificationStateRepository.save(state);
	}

	@Override
	@Transactional
	public void clearVerification(String mobileNumber) {
		String normalizedMobileNumber = ContactNormalizer.normalizeMobileNumber(mobileNumber);
		otpVerificationStateRepository.markUnverifiedByMobileNumber(normalizedMobileNumber);
	}
}
