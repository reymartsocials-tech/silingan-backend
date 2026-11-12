package com.ria.olita.tech.silingan.service.qr;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.RequiredArgsConstructor;

/**
 * Builds the SILINGAN-controlled resident join URL that a community join QR code encodes.
 *
 * <p>The base URL always comes from {@link OnboardingProperties}; a client can never influence the
 * host, so a generated QR can only point at the configured onboarding page.
 *
 * <p>The URL carries a single-purpose invitation token, never the community's permanent manual
 * code, so a forwarded link can be expired or revoked on its own.
 */
@Component
@RequiredArgsConstructor
public class CommunityJoinUrlFactory {

	public static final String INVITATION_TOKEN_PARAM = "token";

	private final OnboardingProperties onboardingProperties;

	public URI create(String invitationToken) {
		if (invitationToken == null || invitationToken.isBlank()) {
			throw new ValidationException("Invitation token is required to build a join URL");
		}

		return UriComponentsBuilder.fromUri(onboardingProperties.joinBaseUri())
			.queryParam(INVITATION_TOKEN_PARAM, invitationToken.trim())
			.build()
			.encode()
			.toUri();
	}
}
