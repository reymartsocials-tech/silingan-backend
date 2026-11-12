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

	@Test
	void acceptsHttpsJoinBaseUrl() {
		OnboardingProperties properties = propertiesWith("https://app.silingan.ph/join");

		assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();
		assertThat(properties.joinBaseUri()).hasToString("https://app.silingan.ph/join");
	}

	@Test
	void allowsPlainHttpOnlyForLocalDevelopment() {
		assertThatCode(() -> propertiesWith("http://localhost:3000/join").validateConfiguration())
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> propertiesWith("http://app.silingan.ph/join").validateConfiguration())
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("HTTPS");
	}

	@Test
	void rejectsBlankConfiguration() {
		assertThatThrownBy(() -> propertiesWith("   ").validateConfiguration())
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> propertiesWith(null).validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsRelativeOrHostlessUrls() {
		assertThatThrownBy(() -> propertiesWith("/join").validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsConflictingQueryStringOrFragment() {
		assertThatThrownBy(() -> propertiesWith("https://app.silingan.ph/join?communityCode=X").validateConfiguration())
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> propertiesWith("https://app.silingan.ph/join#section").validateConfiguration())
			.isInstanceOf(ValidationException.class);
	}
}
