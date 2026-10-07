package com.ria.olita.tech.silingan.config;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the shared Keycloak admin client.
 *
 * <p>The timeouts are the point of this configuration: Keycloak is called from a servlet filter on
 * the request path, so without them an unresponsive Keycloak blocks the request thread forever.
 */
class KeycloakClientConfigTest {

	private final KeycloakClientConfig config = new KeycloakClientConfig();

	private KeycloakProperties properties() {
		KeycloakProperties properties = new KeycloakProperties();
		properties.setUrl("http://localhost:8080");
		properties.setRealm("silingan-platform");
		properties.setClientId("silingan-backend");
		properties.setClientSecret("secret");
		return properties;
	}

	@Test
	void readTimeoutAbortsACallThatNeverGetsAResponse() throws IOException {
		// Accepts the connection then never replies, which is exactly the "Keycloak is wedged"
		// case that used to hang the caller indefinitely.
		try (ServerSocket blackHole = new ServerSocket(0)) {
			KeycloakProperties properties = properties();
			properties.setReadTimeoutMs(750);

			try (Client client = config.keycloakJaxRsClient(properties)) {
				long startedAt = System.nanoTime();

				assertThatThrownBy(() -> client.target("http://localhost:" + blackHole.getLocalPort())
					.request()
					.get())
					.isInstanceOf(ProcessingException.class);

				Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
				assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
			}
		}
	}

	@Test
	void timeoutsDefaultToBoundedValues() {
		KeycloakProperties properties = properties();

		assertThat(properties.getConnectTimeoutMs()).isPositive();
		assertThat(properties.getReadTimeoutMs()).isPositive();
	}

	@Test
	void adminClientIsBuiltOnTheSharedJaxRsClient() {
		KeycloakProperties properties = properties();
		Client jaxRsClient = config.keycloakJaxRsClient(properties);

		try (Keycloak keycloak = config.keycloakAdminClient(properties, jaxRsClient)) {
			assertThat(keycloak).isNotNull();
			assertThat(keycloak.isClosed()).isFalse();
		}
	}
}
