package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.config.OnboardingProperties;

/**
 * Writes inspectable sample join QR images so the encoded payload can be verified by eye and with a
 * real scanner, and proves that neither sample ever carries the community's permanent manual code.
 *
 * <p>Samples are written under {@code target/} because it is build output: generated QR binaries are
 * never committed to the repository and never stored in the database.
 */
class CommunityJoinQrSampleTest {

	/** Shape-accurate stand-in for a real token: 32 random bytes as unpadded URL-safe Base64. */
	private static final String SAMPLE_TOKEN = "qN4rT8wXzB2mK6vJ9pL3sD7fH1gY5cA0eU8iO2tR6xQ";

	private static final String COMMUNITY_CODE = "SANISIDRO";

	private static final Path SAMPLE_DIR = Path.of("target", "qr-samples");

	private final QrCodeGenerator generator = JoinQrTestFixtures.generator();

	@BeforeAll
	static void createSampleDirectory() throws Exception {
		Files.createDirectories(SAMPLE_DIR);
	}

	private static CommunityJoinUrlFactory factory(boolean linkDistributionEnabled) {
		OnboardingProperties properties = JoinQrTestFixtures.properties();
		properties.getInvitation().setLinkDistributionEnabled(linkDistributionEnabled);
		return new CommunityJoinUrlFactory(properties);
	}

	private static String decode(byte[] png) throws Exception {
		return JoinQrTestFixtures.decode(png);
	}

	private String writeSample(String fileName, URI payload) throws Exception {
		byte[] png = generator.generatePng(payload.toString());
		Files.write(SAMPLE_DIR.resolve(fileName), png);

		String decoded = decode(png);
		assertThat(decoded).isEqualTo(payload.toString());
		return decoded;
	}

	@Test
	void writesTheMvpInAppJoinQrSample() throws Exception {
		URI payload = factory(false).createQrPayload(SAMPLE_TOKEN);

		String decoded = writeSample("join-qr-in-app-mvp.png", payload);

		assertThat(decoded)
			.isEqualTo("silingan://join?token=" + SAMPLE_TOKEN)
			.doesNotContain(COMMUNITY_CODE)
			.doesNotContain("communityCode");
	}

	@Test
	void writesTheLinkDistributionJoinQrSample() throws Exception {
		URI payload = factory(true).createQrPayload(SAMPLE_TOKEN);

		String decoded = writeSample("join-qr-link-distribution.png", payload);

		assertThat(decoded)
			.isEqualTo("https://app.silingan.ph/join?token=" + SAMPLE_TOKEN)
			.doesNotContain(COMMUNITY_CODE)
			.doesNotContain("communityCode");
	}
}
