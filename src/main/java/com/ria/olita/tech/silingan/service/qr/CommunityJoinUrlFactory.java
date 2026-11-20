package com.ria.olita.tech.silingan.service.qr;

import java.net.URI;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.RequiredArgsConstructor;

/**
 * Builds the SILINGAN-controlled join target that a community join QR code encodes.
 *
 * <p>Both targets come from {@link OnboardingProperties}; a client can never influence the host or
 * scheme, so a generated QR can only point at SILINGAN.
 *
 * <p>The target carries a single-purpose invitation token, never the community's permanent manual
 * code, so a forwarded QR can be expired or revoked on its own.
 *
 * <p>Which target a QR encodes depends on
 * {@code silingan.onboarding.invitation.link-distribution-enabled}:
 *
 * <ul>
 *   <li><b>disabled</b> (MVP): the app deep link, which a generic camera app cannot open, so the
 *   invitation can only be acted on inside the installed SILINGAN app.</li>
 *   <li><b>enabled</b>: the web join URL, which can additionally be shared as a link.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CommunityJoinUrlFactory {

	public static final String INVITATION_TOKEN_PARAM = "token";

	private final OnboardingProperties onboardingProperties;

	/**
	 * Target encoded into the QR image.
	 */
	public URI createQrPayload(String invitationToken) {
		return onboardingProperties.isLinkDistributionEnabled()
			? createWebJoinUrl(invitationToken)
			: createAppJoinUri(invitationToken);
	}

	/**
	 * Shareable web join link, available only while link distribution is enabled.
	 *
	 * @return empty for the in-app-only MVP, so no caller can accidentally hand an administrator a
	 * link that is not meant to leave the app yet.
	 */
	public Optional<URI> createShareableJoinUrl(String invitationToken) {
		if (!onboardingProperties.isLinkDistributionEnabled()) {
			return Optional.empty();
		}

		return Optional.of(createWebJoinUrl(invitationToken));
	}

	public URI createWebJoinUrl(String invitationToken) {
		return withToken(onboardingProperties.joinBaseUri(), invitationToken);
	}

	public URI createAppJoinUri(String invitationToken) {
		return withToken(onboardingProperties.appJoinUri(), invitationToken);
	}

	private URI withToken(URI base, String invitationToken) {
		if (invitationToken == null || invitationToken.isBlank()) {
			throw new ValidationException("Invitation token is required to build a join target");
		}

		return UriComponentsBuilder.fromUri(base)
			.queryParam(INVITATION_TOKEN_PARAM, invitationToken.trim())
			.build()
			.encode()
			.toUri();
	}
}
