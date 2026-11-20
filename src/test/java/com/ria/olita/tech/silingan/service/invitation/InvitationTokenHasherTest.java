package com.ria.olita.tech.silingan.service.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvitationTokenHasherTest {

	private final InvitationTokenHasher hasher = new InvitationTokenHasher();

	@Test
	void producesTheKnownSha256Digest() {
		assertThat(hasher.hash("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void isDeterministic() {
		assertThat(hasher.hash("Y8hVfM3wQk7xP2nR6cT9")).isEqualTo(hasher.hash("Y8hVfM3wQk7xP2nR6cT9"));
	}

	@Test
	void producesSixtyFourLowercaseHexCharacters() {
		assertThat(hasher.hash("Y8hVfM3wQk7xP2nR6cT9")).matches("^[0-9a-f]{64}$");
	}

	@Test
	void neverReturnsTheRawToken() {
		String rawToken = "Y8hVfM3wQk7xP2nR6cT9";

		assertThat(hasher.hash(rawToken)).doesNotContain(rawToken);
	}

	@Test
	void differentTokensProduceDifferentHashes() {
		assertThat(hasher.hash("token-a")).isNotEqualTo(hasher.hash("token-b"));
	}

	@Test
	void rejectsBlankInput() {
		assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> hasher.hash("  ")).isInstanceOf(IllegalArgumentException.class);
	}
}
