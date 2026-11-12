package com.ria.olita.tech.silingan.service.qr;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.RequiredArgsConstructor;

/**
 * Builds the SILINGAN-controlled resident join URL that a community QR code encodes.
 *
 * <p>The base URL always comes from {@link OnboardingProperties}; a client can never influence the
 * host, so a generated QR can only point at the configured onboarding page.
 */
@Component
@RequiredArgsConstructor
public class CommunityJoinUrlFactory {

	public static final String COMMUNITY_CODE_PARAM = "communityCode";

	private final OnboardingProperties onboardingProperties;

	public URI create(String communityCode) {
		if (communityCode == null || communityCode.isBlank()) {
			throw new ValidationException("Community code is required to build a join URL");
		}

		return UriComponentsBuilder.fromUri(onboardingProperties.joinBaseUri())
			.queryParam(COMMUNITY_CODE_PARAM, communityCode.trim())
			.build()
			.encode()
			.toUri();
	}
}
