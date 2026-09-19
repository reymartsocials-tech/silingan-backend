package com.ria.olita.tech.silingan.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ria.olita.tech.silingan.config.JwtConfig;
import com.ria.olita.tech.silingan.config.JwtProperties;
import com.ria.olita.tech.silingan.entity.User;

class JwtServiceImplTest {

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("test-only-secret-change-me-please-32-bytes-minimum");
		properties.setIssuer("silingan-test");
		properties.setAccessTokenTtlSeconds(3600);

		JwtConfig jwtConfig = new JwtConfig();
		SecretKey key = jwtConfig.jwtSecretKey(properties);
		JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(key);
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
		JwtDecoder jwtDecoder = decoder;

		jwtService = new JwtServiceImpl(jwtEncoder, jwtDecoder, properties);
	}

	@Test
	void generateToken_shouldContainExpectedClaims() {
		User user = User.builder()
			.id(UUID.randomUUID())
			.keycloakUserId("kc-user-123")
			.mobileNumber("+639171234567")
			.firstName("John")
			.lastName("Doe")
			.email("+639171234567"+"@silingan.com")
			.build();

		UUID communityId = UUID.randomUUID();

		String token = jwtService.generateToken(user, List.of("CUSTOMER"), communityId);
		assertTrue(jwtService.validate(token));

		Jwt decoded = jwtService.parse(token).orElseThrow();;
		assertEquals(user.getId().toString(), decoded.getSubject());
		assertEquals("kc-user-123", decoded.getClaimAsString("keycloakId"));
		assertEquals("+639171234567", decoded.getClaimAsString("mobileNumber"));
		assertEquals(List.of("CUSTOMER"), decoded.getClaimAsStringList("roles"));
		assertEquals(communityId.toString(), decoded.getClaimAsString("communityId"));
	}
}


