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
import com.ria.olita.tech.silingan.exception.UnauthorizedException;
import com.ria.olita.tech.silingan.exception.ValidationException;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpHandlerServiceImpl implements OtpHandlerService {

	private final OtpService otpService;
	private final OtpVerificationStateService otpVerificationStateService;
	private final OtpProperties otpProperties;

	@Override
	public OtpResendResponse resendForAuthenticatedUser(HttpServletRequest request) {
		JwtPrincipal principal = resolvePrincipal();
		otpVerificationStateService.clearVerification(principal.keycloakUserId(), principal.tokenId());

		var response = otpService.requestOtp(
			OtpRequest.builder().mobileNumber(principal.mobileNumber()).build(),
			request.getHeader("User-Agent")
		);

		return OtpResendResponse.initiated(response.getCooldownSeconds());
	}

	@Override
	public OtpVerificationResultResponse verifyForAuthenticatedUser(String otp, HttpServletRequest request) {
		JwtPrincipal principal = resolvePrincipal();

		otpService.verifyOtp(
			OtpVerifyRequest.builder()
				.mobileNumber(principal.mobileNumber())
				.otp(otp)
				.build(),
			request.getHeader("User-Agent")
		);

		otpVerificationStateService.markVerified(
			principal.keycloakUserId(),
			principal.tokenId(),
			resolveStateExpiry(principal.jwt())
		);

		return OtpVerificationResultResponse.verified();
	}

	@Override
	public OtpStatusResponse statusForAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return new OtpStatusResponse(false, false);
		}

		JwtPrincipal principal = resolvePrincipal();
		boolean verified = otpVerificationStateService.isVerified(principal.keycloakUserId(), principal.tokenId());
		return new OtpStatusResponse(true, verified);
	}

	private Instant resolveStateExpiry(Jwt jwt) {
		Instant jwtExpiry = jwt.getExpiresAt();
		if (jwtExpiry != null) {
			return jwtExpiry;
		}
		return Instant.now().plusSeconds(otpProperties.getExpirationMinutes() * 60L);
	}

	private JwtPrincipal resolvePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
			throw new UnauthorizedException("Authentication required");
		}

		Jwt jwt = jwtAuthenticationToken.getToken();
		String keycloakUserId = jwt.getClaimAsString("keycloakId");
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			keycloakUserId = jwt.getClaimAsString("sub");
		}
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			throw new UnauthorizedException("Authenticated token missing 'sub' claim");
		}

		String mobileNumber = resolveMobileClaim(jwt);
		if (mobileNumber == null || mobileNumber.isBlank()) {
			throw new ValidationException("Authenticated token missing mobile number claim");
		}

		return new JwtPrincipal(keycloakUserId, jwt.getId(), mobileNumber, jwt);
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

	private String getClientIpAddress(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0].trim();
		}
		String xRealIp = request.getHeader("X-Real-IP");
		if (xRealIp != null && !xRealIp.isEmpty()) {
			return xRealIp;
		}
		return request.getRemoteAddr();
	}

	private record JwtPrincipal(String keycloakUserId, String tokenId, String mobileNumber, Jwt jwt) {
	}
}

