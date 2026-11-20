package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.ValidationException;

class CommunityJoinUrlFactoryTest {

	private static final String TOKEN = "Y8hVfM3wQk7xP2nR6cT9";

	private static CommunityJoinUrlFactory factoryFor(String baseUrl) {
		return factoryFor(baseUrl, false);
	}

	private static CommunityJoinUrlFactory factoryFor(String baseUrl, boolean linkDistributionEnabled) {
		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl(baseUrl);
		properties.getInvitation().setLinkDistributionEnabled(linkDistributionEnabled);
		return new CommunityJoinUrlFactory(properties);
	}

	@Test
	void buildsWebJoinUrlFromConfiguredBaseUrl() {
		assertThat(factoryFor("https://app.silingan.ph/join").createWebJoinUrl(TOKEN))
			.hasToString("https://app.silingan.ph/join?token=" + TOKEN);
	}

	@Test
	void usesTheConfiguredEnvironmentHost() {
		assertThat(factoryFor("https://staging.silingan.ph/join").createWebJoinUrl(TOKEN))
			.hasToString("https://staging.silingan.ph/join?token=" + TOKEN);
	}

	@Test
	void buildsAppDeepLinkFromTheConfiguredAppUri() {
		assertThat(factoryFor("https://app.silingan.ph/join").createAppJoinUri(TOKEN))
			.hasToString("silingan://join?token=" + TOKEN);
	}

	@Test
	void encodesQueryParameterCharactersSafely() {
		assertThat(factoryFor("https://app.silingan.ph/join").createWebJoinUrl("A B&c=d"))
			.hasToString("https://app.silingan.ph/join?token=A%20B%26c%3Dd");
	}

	@Test
	void producesDeterministicOutputForTheSameTokenAndConfiguration() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");
		assertThat(factory.createWebJoinUrl(TOKEN)).isEqualTo(factory.createWebJoinUrl(TOKEN));
	}

	@Test
	void trimsSurroundingWhitespaceInTheToken() {
		assertThat(factoryFor("https://app.silingan.ph/join").createWebJoinUrl("  " + TOKEN + "  "))
			.hasToString("https://app.silingan.ph/join?token=" + TOKEN);
	}

	@Test
	void rejectsMissingInvitationToken() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");

		assertThatThrownBy(() -> factory.createWebJoinUrl(null)).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> factory.createAppJoinUri("   ")).isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsBlankConfiguration() {
		assertThatThrownBy(() -> factoryFor("  ").createWebJoinUrl(TOKEN))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void theHostCanOnlyComeFromConfiguration() {
		// A token that looks like a URL is still a query parameter of the configured host.
		assertThat(factoryFor("https://app.silingan.ph/join").createWebJoinUrl("https://evil.example.com").getHost())
			.isEqualTo("app.silingan.ph");
	}

	// ---------- link distribution toggle ----------

	@Test
	void theQrEncodesTheAppDeepLinkWhileLinkDistributionIsDisabled() {
		// A generic camera app cannot open a silingan:// URI, so the QR is in-app only.
		assertThat(factoryFor("https://app.silingan.ph/join", false).createQrPayload(TOKEN))
			.hasToString("silingan://join?token=" + TOKEN);
	}

	@Test
	void theQrEncodesTheWebJoinUrlOnceLinkDistributionIsEnabled() {
		assertThat(factoryFor("https://app.silingan.ph/join", true).createQrPayload(TOKEN))
			.hasToString("https://app.silingan.ph/join?token=" + TOKEN);
	}

	@Test
	void noShareableLinkIsProducedWhileLinkDistributionIsDisabled() {
		assertThat(factoryFor("https://app.silingan.ph/join", false).createShareableJoinUrl(TOKEN)).isEmpty();
	}

	@Test
	void aShareableLinkIsProducedOnceLinkDistributionIsEnabled() {
		assertThat(factoryFor("https://app.silingan.ph/join", true).createShareableJoinUrl(TOKEN))
			.hasValueSatisfying(uri -> assertThat(uri).hasToString("https://app.silingan.ph/join?token=" + TOKEN));
	}

	@Test
	void theTokenIsCarriedIdenticallyByBothTargets() {
		// Enabling distribution later must not change how a token is transported.
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");

		assertThat(factory.createAppJoinUri(TOKEN).getQuery())
			.isEqualTo(factory.createWebJoinUrl(TOKEN).getQuery());
	}
}
