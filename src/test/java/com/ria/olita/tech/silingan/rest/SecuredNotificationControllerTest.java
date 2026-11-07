package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SecuredNotificationControllerTest {

	@Test
	void shouldRequireAuthenticationOnNotificationEndpoints() {
		PreAuthorize preAuthorize = SecuredNotificationController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).contains("isAuthenticated()");
	}
}
