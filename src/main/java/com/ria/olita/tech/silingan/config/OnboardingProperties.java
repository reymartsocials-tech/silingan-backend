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
	 */
	@NotBlank
	private String joinBaseUrl;

	/**
	 * Parsed and validated join base URL.
	 */
	public URI joinBaseUri() {
		return validatedUri();
	}

	/**
	 * Fails fast at startup instead of producing an unusable QR code at request time.
	 */
	@PostConstruct
	void validateConfiguration() {
		validatedUri();
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
}
