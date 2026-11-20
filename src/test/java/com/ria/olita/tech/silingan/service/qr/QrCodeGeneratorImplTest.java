package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.exception.ValidationException;

class QrCodeGeneratorImplTest {

	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

	private final QrCodeGenerator generator = JoinQrTestFixtures.generator();

	private static String decode(byte[] png) throws Exception {
		return JoinQrTestFixtures.decode(png);
	}

	@Test
	void generatesNonEmptyPngWithValidFileHeaders() {
		byte[] png = generator.generatePng("silingan://join?token=Y8hVfM3wQk7xP2nR6cT9");

		assertThat(png).isNotEmpty();
		assertThat(Arrays.copyOf(png, PNG_MAGIC.length)).isEqualTo(PNG_MAGIC);
	}

	@Test
	void encodedContentDecodesBackToTheJoinTarget() throws Exception {
		String joinTarget = "https://app.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9";

		assertThat(decode(generator.generatePng(joinTarget))).isEqualTo(joinTarget);
	}

	@Test
	void handlesTokensRequiringUrlEncoding() throws Exception {
		String joinTarget = "https://app.silingan.ph/join?token=A%20B%26c%3Dd";

		assertThat(decode(generator.generatePng(joinTarget))).isEqualTo(joinTarget);
	}

	@Test
	void rejectsBlankContentThroughTheStandardValidationException() {
		assertThatThrownBy(() -> generator.generatePng("")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> generator.generatePng(null)).isInstanceOf(ValidationException.class);
	}
}
