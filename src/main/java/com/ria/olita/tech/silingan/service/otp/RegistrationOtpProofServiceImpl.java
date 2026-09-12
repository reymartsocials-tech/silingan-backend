package com.ria.olita.tech.silingan.service.otp;

import com.ria.olita.tech.silingan.util.ContactNormalizer;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationOtpProofServiceImpl implements RegistrationOtpProofService {

	private final Cache<String, Boolean> registrationOtpProofCache;

	@Override
	public void markVerifiedForRegistration(String mobileNumber) {
		String normalized = normalizePhoneNumber(mobileNumber);
		if (normalized == null || normalized.isBlank()) {
			return;
		}
		registrationOtpProofCache.put(normalized, Boolean.TRUE);
	}

	@Override
	public boolean consumeRegistrationProof(String mobileNumber) {
		String normalized = normalizePhoneNumber(mobileNumber);
		if (normalized == null || normalized.isBlank()) {
			return false;
		}
		Boolean verified = registrationOtpProofCache.getIfPresent(normalized);
		if (Boolean.TRUE.equals(verified)) {
			registrationOtpProofCache.invalidate(normalized);
			return true;
		}
		return false;
	}

	private String normalizePhoneNumber(String phoneNumber) {
		return ContactNormalizer.normalizeMobileNumber(phoneNumber);
	}
}

