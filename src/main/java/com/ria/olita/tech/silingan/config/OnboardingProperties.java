package com.ria.olita.tech.silingan.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.ria.olita.tech.silingan.exception.ValidationException;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for resident onboarding entry points.
 * Configurable via application.yaml under the 'silingan.onboarding' prefix.
 *
 * <p>The join base URL is the only URL the backend is allowed to encode into a community join QR
 * code. It is never taken from client input so that a scanned QR can only ever point at a
 * SILINGAN-controlled onboarding page.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "silingan.onboarding")
public class OnboardingProperties {

	private static final String LOCAL_HOST = "localhost";
	private static final String LOOPBACK_HOST = "127.0.0.1";

	/**
	 * Base URL of the resident join page, e.g. {@code https://app.silingan.ph/join}.
	 *
	 * <p>Only used when invitation link distribution is enabled.
	 */
	@NotBlank
	private String joinBaseUrl;

	/**
	 * Deep-link base of the installed SILINGAN mobile app, e.g. {@code silingan://join}.
	 *
	 * <p>This is what a join QR encodes while link distribution is disabled. A non-web scheme means
	 * a generic camera app cannot open it, so the invitation can only be acted on by scanning or
	 * uploading the image inside the installed SILINGAN app.
	 */
	@NotBlank
	private String appJoinUri = "silingan://join";

	/**
	 * Safe bounds an administrator may choose from when creating a join invitation.
	 */
	private final Invitation invitation = new Invitation();

	/**
	 * Parsed and validated join base URL.
	 */
	public URI joinBaseUri() {
		return validatedUri();
	}

	/**
	 * Parsed and validated SILINGAN app deep-link base.
	 */
	public URI appJoinUri() {
		return validatedAppUri();
	}

	/**
	 * Whether an invitation may be distributed as a shareable web link.
	 *
	 * <p>Disabled for the MVP: the invitation token subsystem is fully in place, but onboarding is
	 * restricted to in-app QR scanning. Enabling this property is the only change needed to start
	 * distributing join links through email or Viber.
	 */
	public boolean isLinkDistributionEnabled() {
		return invitation.isLinkDistributionEnabled();
	}

	/**
	 * Fails fast at startup instead of producing an unusable QR code at request time.
	 */
	@PostConstruct
	void validateConfiguration() {
		validatedAppUri();
		invitation.validate();

		// The web join URL only has to be usable once links may actually be distributed.
		if (invitation.isLinkDistributionEnabled()) {
			validatedUri();
		}
	}

	private URI validatedUri() {
		if (joinBaseUrl == null || joinBaseUrl.isBlank()) {
			throw new ValidationException("silingan.onboarding.join-base-url must be configured");
		}

		URI uri;
		try {
			uri = new URI(joinBaseUrl.trim());
		} catch (URISyntaxException ex) {
			throw new ValidationException("silingan.onboarding.join-base-url is not a valid URL");
		}

		if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
			throw new ValidationException("silingan.onboarding.join-base-url must be an absolute URL with a host");
		}

		String scheme = uri.getScheme().toLowerCase();
		if (!"https".equals(scheme) && !isLocalHost(uri.getHost())) {
			throw new ValidationException("silingan.onboarding.join-base-url must use HTTPS outside local development");
		}

		if (uri.getQuery() != null || uri.getFragment() != null) {
			throw new ValidationException("silingan.onboarding.join-base-url must not contain a query string or fragment");
		}

		return uri;
	}

	private boolean isLocalHost(String host) {
		return LOCAL_HOST.equalsIgnoreCase(host) || LOOPBACK_HOST.equals(host);
	}

	private URI validatedAppUri() {
		if (appJoinUri == null || appJoinUri.isBlank()) {
			throw new ValidationException("silingan.onboarding.app-join-uri must be configured");
		}

		URI uri;
		try {
			uri = new URI(appJoinUri.trim());
		} catch (URISyntaxException ex) {
			throw new ValidationException("silingan.onboarding.app-join-uri is not a valid URI");
		}

		if (!uri.isAbsolute() || uri.getScheme() == null || uri.getScheme().isBlank()) {
			throw new ValidationException("silingan.onboarding.app-join-uri must be an absolute URI with a scheme");
		}

		String scheme = uri.getScheme().toLowerCase();
		if ("http".equals(scheme) || "https".equals(scheme)) {
			throw new ValidationException(
				"silingan.onboarding.app-join-uri must use a SILINGAN app scheme, not http or https"
			);
		}

		// An opaque URI such as "silingan:join" cannot carry a query parameter safely.
		if (uri.isOpaque()) {
			throw new ValidationException("silingan.onboarding.app-join-uri must be hierarchical, e.g. silingan://join");
		}

		if (uri.getQuery() != null || uri.getFragment() != null) {
			throw new ValidationException("silingan.onboarding.app-join-uri must not contain a query string or fragment");
		}

		return uri;
	}

	/**
	 * Bounds applied to administrator-supplied invitation expiry and usage limits, so a leaked QR
	 * can never be configured to live forever or be used an unbounded number of times.
	 */
	@Getter
	@Setter
	public static class Invitation {

		/**
		 * Whether an invitation may be shared as a web link outside the SILINGAN app.
		 *
		 * <p>Off for the MVP, where onboarding is in-app only. Turning it on makes the QR encode the
		 * web join URL and makes the one-time shareable link available to administrators. Nothing
		 * else about the invitation lifecycle changes.
		 */
		private boolean linkDistributionEnabled = false;

		private int minExpiryHours = 1;

		private int defaultExpiryHours = 168;

		private int maxExpiryHours = 720;

		private int minMaxUses = 1;

		private int defaultMaxUses = 100;

		private int maxMaxUses = 500;

		void validate() {
			if (minExpiryHours < 1 || maxExpiryHours < minExpiryHours) {
				throw new ValidationException("silingan.onboarding.invitation expiry bounds are invalid");
			}
			if (defaultExpiryHours < minExpiryHours || defaultExpiryHours > maxExpiryHours) {
				throw new ValidationException("silingan.onboarding.invitation.default-expiry-hours must be within the configured bounds");
			}
			if (minMaxUses < 1 || maxMaxUses < minMaxUses) {
				throw new ValidationException("silingan.onboarding.invitation usage bounds are invalid");
			}
			if (defaultMaxUses < minMaxUses || defaultMaxUses > maxMaxUses) {
				throw new ValidationException("silingan.onboarding.invitation.default-max-uses must be within the configured bounds");
			}
		}
	}
}
