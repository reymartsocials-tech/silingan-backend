package com.ria.olita.tech.silingan.service.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureInvitationTokenGeneratorTest {

	private final SecureInvitationTokenGenerator generator = new SecureInvitationTokenGenerator();

	@Test
	void generatesThirtyTwoBytesOfRandomness() {
		byte[] decoded = Base64.getUrlDecoder().decode(generator.generate());

		assertThat(decoded).hasSize(SecureInvitationTokenGenerator.TOKEN_BYTE_LENGTH);
	}

	@Test
	void generatesUrlSafeUnpaddedTokens() {
		String token = generator.generate();

		assertThat(token)
			.hasSize(SecureInvitationTokenGenerator.TOKEN_LENGTH)
			.matches("^[A-Za-z0-9_-]+$")
			.doesNotContain("=");
	}

	@Test
	void neverRepeatsATokenInPractice() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			tokens.add(generator.generate());
		}

		assertThat(tokens).hasSize(1_000);
	}

	@Test
	void doesNotEncodeAnySharedPrefix() {
		// Guards against a timestamp or counter sneaking into the token.
		String first = generator.generate();
		String second = generator.generate();

		assertThat(first.substring(0, 8)).isNotEqualTo(second.substring(0, 8));
	}
}
