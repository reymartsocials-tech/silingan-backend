package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.ValidationException;

class CommunityJoinUrlFactoryTest {

	private static CommunityJoinUrlFactory factoryFor(String baseUrl) {
		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl(baseUrl);
		return new CommunityJoinUrlFactory(properties);
	}

	@Test
	void buildsJoinUrlFromConfiguredBaseUrl() {
		assertThat(factoryFor("https://app.silingan.ph/join").create("MAPLERES-2026"))
			.hasToString("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
	}

	@Test
	void usesTheConfiguredEnvironmentHost() {
		assertThat(factoryFor("https://staging.silingan.ph/join").create("MAPLERES-2026"))
			.hasToString("https://staging.silingan.ph/join?communityCode=MAPLERES-2026");
	}

	@Test
	void encodesQueryParameterCharactersSafely() {
		assertThat(factoryFor("https://app.silingan.ph/join").create("A B&c=d"))
			.hasToString("https://app.silingan.ph/join?communityCode=A%20B%26c%3Dd");
	}

	@Test
	void producesDeterministicOutputForTheSameCodeAndConfiguration() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");
		assertThat(factory.create("MAPLERES-2026")).isEqualTo(factory.create("MAPLERES-2026"));
	}

	@Test
	void trimsSurroundingWhitespaceInTheCode() {
		assertThat(factoryFor("https://app.silingan.ph/join").create("  MAPLERES-2026  "))
			.hasToString("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
	}

	@Test
	void rejectsMissingCommunityCode() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");

		assertThatThrownBy(() -> factory.create(null)).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> factory.create("   ")).isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsBlankConfiguration() {
		assertThatThrownBy(() -> factoryFor("  ").create("MAPLERES-2026"))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void theHostCanOnlyComeFromConfiguration() {
		// A code that looks like a URL is still a query parameter of the configured host.
		assertThat(factoryFor("https://app.silingan.ph/join").create("https://evil.example.com").getHost())
			.isEqualTo("app.silingan.ph");
	}
}
