package com.ria.olita.tech.silingan.config;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
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
	 * Presentation of the generated join QR image. Visual only: nothing here reaches the payload.
	 */
	private final Qr qr = new Qr();

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
		qr.validate();

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

	/**
	 * Branding of the join QR image (SILINGAN "Dark Background" card).
	 *
	 * <p>Presentation only: none of these values is ever encoded into the QR payload, and none of
	 * them is reachable from client input. Scanning reliability constrains the bounds enforced in
	 * {@link #validate()}: the quiet zone may not drop below four modules and the centre logo may
	 * not cover more than {@value #CENTER_LOGO_MAX_RATIO_LIMIT} of the QR, because the logo already
	 * consumes part of the error-correction budget.
	 */
	@Getter
	@Setter
	public static class Qr {

		/** Absolute ceiling for the centre logo, beyond which decoding stops being dependable. */
		public static final double CENTER_LOGO_MAX_RATIO_LIMIT = 0.20;

		/** QR specification minimum quiet zone. */
		public static final int MIN_MARGIN_MODULES = 4;

		private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

		private int canvasWidth = 640;

		private int canvasHeight = 780;

		/** Maximum edge of the QR panel. The matrix is scaled to whole modules inside it. */
		private int qrSize = 544;

		private int topPadding = 40;

		private int sidePadding = 48;

		/** Vertical gap between the QR panel and the SILINGAN wordmark. */
		private int brandingGap = 26;

		/** Vertical gap between the wordmark and the tagline. */
		private int taglineGap = 10;

		private int bottomPadding = 36;

		private double centerLogoMaxRatio = 0.18;

		/** Padding between the white centre backing plate and the logo drawn inside it. */
		private int centerLogoPadding = 12;

		private String errorCorrection = "H";

		private int marginModules = MIN_MARGIN_MODULES;

		/** Dark SILINGAN card background. */
		private String backgroundColor = "#14332C";

		/**
		 * Modules are drawn dark on a white panel. ZXing and every tested camera decode this
		 * reliably; a fully inverted matrix does not (see docs/COMMUNITY_JOIN_QR.md).
		 */
		private String qrForegroundColor = "#000000";

		private String qrBackgroundColor = "#FFFFFF";

		private String wordmarkColor = "#FFFFFF";

		private String taglineColor = "#E8F3F1";

		private int wordmarkFontSize = 40;

		private int taglineFontSize = 22;

		private String logoResource = "classpath:branding/silingan-icon.png";

		/** Approved tagline. Fixed branding copy: never administrator- or request-supplied. */
		private String tagline = "Your community, in one place.";

		public Color backgroundColorValue() {
			return parseColor("background-color", backgroundColor);
		}

		public Color qrForegroundColorValue() {
			return parseColor("qr-foreground-color", qrForegroundColor);
		}

		public Color qrBackgroundColorValue() {
			return parseColor("qr-background-color", qrBackgroundColor);
		}

		public Color wordmarkColorValue() {
			return parseColor("wordmark-color", wordmarkColor);
		}

		public Color taglineColorValue() {
			return parseColor("tagline-color", taglineColor);
		}

		public ErrorCorrectionLevel errorCorrectionLevel() {
			try {
				return ErrorCorrectionLevel.valueOf(errorCorrection == null ? "" : errorCorrection.trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				throw new ValidationException("silingan.onboarding.qr.error-correction must be one of L, M, Q, H");
			}
		}

		void validate() {
			if (canvasWidth < 1 || canvasHeight < 1 || qrSize < 1) {
				throw new ValidationException("silingan.onboarding.qr dimensions must be positive");
			}
			if (topPadding < 0 || sidePadding < 0 || bottomPadding < 0 || brandingGap < 0 || taglineGap < 0) {
				throw new ValidationException("silingan.onboarding.qr paddings and gaps must not be negative");
			}
			if (qrSize + 2 * sidePadding > canvasWidth) {
				throw new ValidationException("silingan.onboarding.qr.qr-size does not fit the canvas width with side padding");
			}
			if (topPadding + qrSize + brandingGap + wordmarkFontSize + taglineGap + taglineFontSize + bottomPadding
				> canvasHeight) {
				throw new ValidationException("silingan.onboarding.qr composition does not fit the canvas height");
			}
			if (centerLogoMaxRatio <= 0 || centerLogoMaxRatio > CENTER_LOGO_MAX_RATIO_LIMIT) {
				throw new ValidationException(
					"silingan.onboarding.qr.center-logo-max-ratio must be greater than 0 and at most "
						+ CENTER_LOGO_MAX_RATIO_LIMIT
				);
			}
			if (centerLogoPadding < 0 || centerLogoPadding * 2 >= qrSize * centerLogoMaxRatio) {
				throw new ValidationException("silingan.onboarding.qr.center-logo-padding leaves no room for the logo");
			}
			if (marginModules < MIN_MARGIN_MODULES) {
				throw new ValidationException(
					"silingan.onboarding.qr.margin-modules must keep at least " + MIN_MARGIN_MODULES + " quiet-zone modules"
				);
			}
			if (wordmarkFontSize < 1 || taglineFontSize < 1) {
				throw new ValidationException("silingan.onboarding.qr font sizes must be positive");
			}
			if (logoResource == null || logoResource.isBlank()) {
				throw new ValidationException("silingan.onboarding.qr.logo-resource must be configured");
			}
			if (tagline == null || tagline.isBlank()) {
				throw new ValidationException("silingan.onboarding.qr.tagline must not be blank");
			}
			errorCorrectionLevel();
			backgroundColorValue();
			qrForegroundColorValue();
			qrBackgroundColorValue();
			wordmarkColorValue();
			taglineColorValue();
		}

		private Color parseColor(String property, String value) {
			if (value == null || !HEX_COLOR.matcher(value.trim()).matches()) {
				throw new ValidationException("silingan.onboarding.qr." + property + " must be a #RRGGBB colour");
			}
			return new Color(Integer.parseInt(value.trim().substring(1), 16));
		}
	}
}
