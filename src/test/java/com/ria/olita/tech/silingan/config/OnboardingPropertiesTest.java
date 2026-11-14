package com.ria.olita.tech.silingan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
}
