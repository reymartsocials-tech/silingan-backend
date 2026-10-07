package com.ria.olita.tech.silingan.config;

import java.util.concurrent.TimeUnit;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

/**
 * Supplies the Keycloak admin client as a single application-scoped bean.
 *
 * <p>Previously every call site built its own {@link Keycloak} instance. That meant a fresh
 * connection pool, a fresh TLS handshake and a fresh {@code client_credentials} token request per
 * invocation - on a request-scoped hot path - and because the instances were never closed their
 * pools and worker threads leaked for the lifetime of the JVM. A singleton reuses both the
 * connection pool and the cached access token.
 *
 * <p>The underlying JAX-RS client carries explicit connect and read timeouts. Without them a slow
 * or unreachable Keycloak blocks the calling request thread indefinitely, which turns a Keycloak
 * outage into an outage of this service.
 */
@Configuration
public class KeycloakClientConfig {

	@Bean(destroyMethod = "close")
	public Client keycloakJaxRsClient(KeycloakProperties properties) {
		return ClientBuilder.newBuilder()
			// Keycloak's own client builder registers this provider; its ObjectMapper tolerates
			// unknown properties, which is what keeps the admin API usable across Keycloak
			// versions. Supplying our own Client means we have to register it ourselves.
			.register(JacksonProvider.class)
			.connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
			.readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
			.build();
	}

	@Bean(destroyMethod = "close")
	public Keycloak keycloakAdminClient(KeycloakProperties properties, Client keycloakJaxRsClient) {
		return KeycloakBuilder.builder()
			.serverUrl(properties.getUrl())
			.realm(properties.getRealm())
			.clientId(properties.getClientId())
			.clientSecret(properties.getClientSecret())
			.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
			.resteasyClient(keycloakJaxRsClient)
			.build();
	}
}
