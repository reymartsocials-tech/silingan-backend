package com.ria.olita.tech.silingan.service.otp;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.config.OtpProperties;
import com.ria.olita.tech.silingan.dto.req.OtpRequest;
import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResendResponse;
import com.ria.olita.tech.silingan.dto.res.OtpStatusResponse;
import com.ria.olita.tech.silingan.dto.res.OtpVerificationResultResponse;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.UnauthorizedException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.util.ContactNormalizer;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpHandlerServiceImpl implements OtpHandlerService {

	private final OtpService otpService;
	private final OtpVerificationStateService otpVerificationStateService;
	private final OtpProperties otpProperties;
	private final UserRepository userRepository;

	@Override
	public OtpResendResponse resendForAuthenticatedUser(HttpServletRequest request) {
		JwtPrincipal principal = resolvePrincipal();
		otpVerificationStateService.clearVerification(principal.mobileNumber());

		var response = otpService.requestOtp(
			OtpRequest.builder()
				.mobileNumber(principal.mobileNumber())
				.build(),
			request.getHeader("User-Agent")
		);

		return OtpResendResponse.initiated(response.getCooldownSeconds());
	}

	@Override
	public OtpVerificationResultResponse verify(OtpVerifyRequest otpVerifyRequest) {
		String mobileNumber = ContactNormalizer.normalizeMobileNumber(otpVerifyRequest.getMobileNumber());
		Optional<User> user = userRepository.findByMobileNumber(mobileNumber);
		if (user.isEmpty()) {
			throw new ValidationException("No user found for the provided mobile number");
		}

		otpService.verifyOtp(
			OtpVerifyRequest.builder()
				.mobileNumber(mobileNumber)
				.otp(otpVerifyRequest.getOtp())
				.build(),
			"system"
		);

		otpVerificationStateService.markVerified(
			mobileNumber,
			resolveStateExpiry()
		);

		return OtpVerificationResultResponse.verified();
	}

	@Override
	public OtpStatusResponse statusForAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext()
			.getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return new OtpStatusResponse(false, false);
		}

		JwtPrincipal principal = resolvePrincipal();
		boolean verified = otpVerificationStateService.isVerified(principal.mobileNumber());
		return new OtpStatusResponse(true, verified);
	}

	private Instant resolveStateExpiry() {
		return Instant.now()
			.plusSeconds(otpProperties.getExpirationMinutes() * 60L);
	}

	private JwtPrincipal resolvePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext()
			.getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
			throw new UnauthorizedException("Authentication required");
		}

		Jwt jwt = jwtAuthenticationToken.getToken();
		String mobileNumber = resolveMobileClaim(jwt);
		if (mobileNumber == null || mobileNumber.isBlank()) {
			throw new ValidationException("Authenticated token missing mobile number claim");
		}

		return new JwtPrincipal(mobileNumber, jwt);
	}

	private String resolveMobileClaim(Jwt jwt) {
		String phoneNumber = jwt.getClaimAsString("phone_number");
		if (phoneNumber != null && !phoneNumber.isBlank()) {
			return phoneNumber;
		}

		String mobileNumber = jwt.getClaimAsString("mobile_number");
		if (mobileNumber != null && !mobileNumber.isBlank()) {
			return mobileNumber;
		}

		return jwt.getClaimAsString("mobileNumber");
	}


	private record JwtPrincipal(String mobileNumber, Jwt jwt) {
	}
}
