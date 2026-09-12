package com.ria.olita.tech.silingan.service.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.config.JwtProperties;
import com.ria.olita.tech.silingan.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final JwtProperties jwtProperties;

	@Override
	public String generateToken(User user, List<String> roles, UUID communityId) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

		JwtClaimsSet claimsSet = JwtClaimsSet.builder()
			.subject(user.getId().toString())
			.issuer(jwtProperties.getIssuer())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.claim("keycloakId", user.getKeycloakUserId())
			.claim("mobileNumber", user.getMobileNumber())
			.claim("firstName", user.getFirstName())
			.claim("lastName", user.getLastName())
			.claim("email", user.getEmail())
			.claim("roles", roles)
			.claim("communityId", communityId)
			.build();

		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
	}

	@Override
	public Optional<Jwt> parse(String token) {
		try {
			return Optional.of(jwtDecoder.decode(token));
		} catch (Exception ex) {
			return Optional.empty();
		}
	}

	@Override
	public boolean validate(String token) {
		return parse(token).isPresent();
	}

	@Override
	public Optional<String> extractSubject(String token) {
		return parse(token).map(Jwt::getSubject);
	}

}


