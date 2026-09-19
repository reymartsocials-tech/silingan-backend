package com.ria.olita.tech.silingan.service.auth;

import com.ria.olita.tech.silingan.util.ContactNormalizer;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.config.JwtProperties;
import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.LoginResponse;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.otp.OtpService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

	private final OtpService otpService;
	private final UserRepository userRepository;
	private final KeycloakService keycloakService;
	private final JwtService jwtService;
	private final JwtProperties jwtProperties;

	@Override
	@Transactional
	public LoginResponse loginWithOtp(OtpVerifyRequest request, String userAgent) {
		otpService.verifyOtp(request, userAgent);
		User user = userRepository.findByMobileNumber(normalizePhoneNumber(request.getMobileNumber()))
			.orElseThrow(() -> new NotFoundException("User not found for mobile number"));
		return buildLoginResponse(user);
	}

	@Override
	@Transactional(readOnly = true)
	public LoginResponse issueTokenForMobile(String mobileNumber, UUID communityId) {
		User user = userRepository.findByMobileNumber(mobileNumber)
			.orElseThrow(() -> new NotFoundException("User not found for mobile number"));
		return buildLoginResponse(user);
	}

	private LoginResponse buildLoginResponse(User user) {
		List<String> roles = keycloakService.getRealmRoles(user.getKeycloakUserId());
		UUID communityId = user.getLastSelectedCommunity() != null ? user.getLastSelectedCommunity().getId() : null;
		String token = jwtService.generateToken(user, roles, communityId);
		return new LoginResponse(
			token,
			"Bearer",
			jwtProperties.getAccessTokenTtlSeconds(),
			user.getId().toString(),
			user.getKeycloakUserId(),
			user.getMobileNumber(),
			roles
		);
	}

	private String normalizePhoneNumber(String phoneNumber) {
		return ContactNormalizer.normalizeMobileNumber(phoneNumber);
	}
}

