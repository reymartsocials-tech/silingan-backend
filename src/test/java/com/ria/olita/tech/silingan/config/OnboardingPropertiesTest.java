package com.ria.olita.tech.silingan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ria.olita.tech.silingan.exception.ValidationException;

class OnboardingPropertiesTest {

	private static OnboardingProperties propertiesWith(String joinBaseUrl) {
		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl(joinBaseUrl);
		return properties;
	}

	/** The web join URL is only enforced once links may actually be distributed. */
	private static OnboardingProperties distributingPropertiesWith(String joinBaseUrl) {
		OnboardingProperties properties = propertiesWith(joinBaseUrl);
		properties.getInvitation().setLinkDistributionEnabled(true);
		return properties;
	}

	@Test
	void acceptsHttpsJoinBaseUrl() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");

		assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();
		assertThat(properties.joinBaseUri()).hasToString("https://app.silingan.ph/join");
	}

	@Test
	void linkDistributionIsDisabledByDefaultForInAppOnlyOnboarding() {
		assertThat(propertiesWith("https://app.silingan.ph/join").isLinkDistributionEnabled()).isFalse();
	}

	@Test
	void defaultsToTheSilinganAppDeepLink() {
		assertThat(propertiesWith("https://app.silingan.ph/join").appJoinUri())
			.hasToString("silingan://join");
	}

	@Test
	void rejectsAnAppJoinUriThatAGenericBrowserCouldOpen() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.setAppJoinUri("https://app.silingan.ph/join");

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("not http or https");
	}

	@Test
	void rejectsAnInvalidAppJoinUri() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");

		properties.setAppJoinUri("   ");
		assertThatThrownBy(properties::validateConfiguration).isInstanceOf(ValidationException.class);

		properties.setAppJoinUri("join");
		assertThatThrownBy(properties::validateConfiguration).isInstanceOf(ValidationException.class);

		properties.setAppJoinUri("silingan:join");
		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("hierarchical");

		properties.setAppJoinUri("silingan://join?token=X");
		assertThatThrownBy(properties::validateConfiguration).isInstanceOf(ValidationException.class);
	}

	@Test
	void theWebJoinUrlIsOnlyValidatedOnceLinksMayBeDistributed() {
		// The MVP never builds a web link, so a placeholder base URL must not block startup.
		OnboardingProperties properties = propertiesWith("http://not-yet-configured.example.com/join");

		assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();

		properties.getInvitation().setLinkDistributionEnabled(true);
		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("HTTPS");
	}

	@Test
	void allowsPlainHttpOnlyForLocalDevelopment() {
		assertThatCode(() -> distributingPropertiesWith("http://localhost:3000/join").validateConfiguration())
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> distributingPropertiesWith("http://app.silingan.ph/join").validateConfiguration())
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("HTTPS");
	}

	@Test
	void rejectsBlankConfiguration() {
		assertThatThrownBy(() -> distributingPropertiesWith("   ").validateConfiguration())
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> distributingPropertiesWith(null).validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsRelativeOrHostlessUrls() {
		assertThatThrownBy(() -> distributingPropertiesWith("/join").validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsConflictingQueryStringOrFragment() {
		assertThatThrownBy(() -> distributingPropertiesWith("https://app.silingan.ph/join?token=X").validateConfiguration())
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> distributingPropertiesWith("https://app.silingan.ph/join#section").validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void appliesTheApprovedInvitationDefaults() {
		OnboardingProperties.Invitation invitation = propertiesWith("https://app.silingan.ph/join").getInvitation();

		assertThat(invitation.getDefaultExpiryHours()).isEqualTo(168);
		assertThat(invitation.getMaxExpiryHours()).isEqualTo(720);
		assertThat(invitation.getMinExpiryHours()).isEqualTo(1);
		assertThat(invitation.getDefaultMaxUses()).isEqualTo(100);
		assertThat(invitation.getMaxMaxUses()).isEqualTo(500);
		assertThat(invitation.getMinMaxUses()).isEqualTo(1);
	}

	@Test
	void rejectsInvitationDefaultsOutsideTheirOwnBounds() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getInvitation().setDefaultExpiryHours(5_000);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("default-expiry-hours");
	}

	@Test
	void rejectsInvertedInvitationBounds() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getInvitation().setMaxMaxUses(0);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("usage bounds");
	}
	@Test
	void acceptsTheApprovedBrandedQrDefaults() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");

		assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();

		OnboardingProperties.Qr qr = properties.getQr();
		assertThat(qr.getCanvasWidth()).isEqualTo(640);
		assertThat(qr.getCanvasHeight()).isEqualTo(780);
		assertThat(qr.getQrSize()).isEqualTo(544);
		assertThat(qr.getTagline()).isEqualTo("Your community, in one place.");
		assertThat(qr.errorCorrectionLevel()).isEqualTo(ErrorCorrectionLevel.H);
		assertThat(qr.getMarginModules()).isGreaterThanOrEqualTo(OnboardingProperties.Qr.MIN_MARGIN_MODULES);
		assertThat(qr.getCenterLogoMaxRatio())
			.isLessThanOrEqualTo(OnboardingProperties.Qr.CENTER_LOGO_MAX_RATIO_LIMIT);
	}

	@Test
	void rejectsAQrLargerThanItsCanvas() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setQrSize(640);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("qr-size");
	}

	@Test
	void rejectsACompositionTallerThanItsCanvas() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setCanvasHeight(600);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("canvas height");
	}

	/** The centre mark spends error-correction budget, so its ceiling is not negotiable. */
	@Test
	void rejectsACentreLogoLargerThanTheApprovedCeiling() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setCenterLogoMaxRatio(0.3);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("center-logo-max-ratio");
	}

	@Test
	void rejectsAQuietZoneBelowTheQrSpecificationMinimum() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setMarginModules(2);

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("margin-modules");
	}

	@Test
	void rejectsAnUnsupportedErrorCorrectionLevel() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setErrorCorrection("X");

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("error-correction");
	}

	@Test
	void rejectsAColourThatCannotBeParsed() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setBackgroundColor("dark-green");

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("background-color");
	}

	@Test
	void rejectsABlankTagline() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setTagline("  ");

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("tagline");
	}

	@Test
	void rejectsABlankBrandingResource() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");
		properties.getQr().setLogoResource("");

		assertThatThrownBy(properties::validateConfiguration)
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("logo-resource");
	}
}
