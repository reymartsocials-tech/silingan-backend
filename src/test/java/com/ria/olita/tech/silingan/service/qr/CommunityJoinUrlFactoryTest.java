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
		assertThat(factoryFor("https://app.silingan.ph/join").create("Y8hVfM3wQk7xP2nR6cT9"))
			.hasToString("https://app.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9");
	}

	@Test
	void usesTheConfiguredEnvironmentHost() {
		assertThat(factoryFor("https://staging.silingan.ph/join").create("Y8hVfM3wQk7xP2nR6cT9"))
			.hasToString("https://staging.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9");
	}

	@Test
	void encodesQueryParameterCharactersSafely() {
		assertThat(factoryFor("https://app.silingan.ph/join").create("A B&c=d"))
			.hasToString("https://app.silingan.ph/join?token=A%20B%26c%3Dd");
	}

	@Test
	void producesDeterministicOutputForTheSameTokenAndConfiguration() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");
		assertThat(factory.create("Y8hVfM3wQk7xP2nR6cT9")).isEqualTo(factory.create("Y8hVfM3wQk7xP2nR6cT9"));
	}

	@Test
	void trimsSurroundingWhitespaceInTheToken() {
		assertThat(factoryFor("https://app.silingan.ph/join").create("  Y8hVfM3wQk7xP2nR6cT9  "))
			.hasToString("https://app.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9");
	}

	@Test
	void rejectsMissingInvitationToken() {
		CommunityJoinUrlFactory factory = factoryFor("https://app.silingan.ph/join");

		assertThatThrownBy(() -> factory.create(null)).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> factory.create("   ")).isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsBlankConfiguration() {
		assertThatThrownBy(() -> factoryFor("  ").create("Y8hVfM3wQk7xP2nR6cT9"))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void theHostCanOnlyComeFromConfiguration() {
		// A token that looks like a URL is still a query parameter of the configured host.
		assertThat(factoryFor("https://app.silingan.ph/join").create("https://evil.example.com").getHost())
			.isEqualTo("app.silingan.ph");
	}
}
