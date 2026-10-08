package com.ria.olita.tech.silingan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Configuration
@ConfigurationProperties(prefix = "keycloak")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeycloakProperties {
	private String url;
	private String realm;
	private String clientId;
	private String clientSecret;
	private String tokenClientId;
	private String tokenClientSecret;
	private String invitationRedirectClientId;
	private String invitationRedirectUri;
	private Integer invitationLifespanSeconds;

	/**
	 * Bounds on the admin client's HTTP calls. Keycloak sits on the request path (invitation
	 * activation runs in a servlet filter), so an unbounded wait here stalls the whole request.
	 */
	private int connectTimeoutMs = 3000;
	private int readTimeoutMs = 5000;
}
