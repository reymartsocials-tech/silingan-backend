package com.ria.olita.tech.silingan.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactNormalizerTest {

	@Test
	void shouldCanonicaliseLocalMobileFormats() {
		assertThat(ContactNormalizer.normalizeMobileNumber("09171234567")).isEqualTo("+639171234567");
		assertThat(ContactNormalizer.normalizeMobileNumber("+639171234567")).isEqualTo("+639171234567");
		assertThat(ContactNormalizer.normalizeMobileNumber("639171234567")).isEqualTo("+639171234567");
	}

	@Test
	void shouldStripSeparatorsSoFormattingCannotBypassUniqueness() {
		assertThat(ContactNormalizer.normalizeMobileNumber("0917-123-4567")).isEqualTo("+639171234567");
		assertThat(ContactNormalizer.normalizeMobileNumber(" +63 917 123 4567 ")).isEqualTo("+639171234567");
		assertThat(ContactNormalizer.normalizeMobileNumber("(0917) 123.4567")).isEqualTo("+639171234567");
	}

	@Test
	void shouldTreatBlankContactDetailsAsNull() {
		assertThat(ContactNormalizer.normalizeMobileNumber("   ")).isNull();
		assertThat(ContactNormalizer.normalizeMobileNumber(null)).isNull();
		assertThat(ContactNormalizer.normalizeEmail("  ")).isNull();
		assertThat(ContactNormalizer.normalizeEmail(null)).isNull();
	}

	@Test
	void shouldLowerCaseEmail() {
		assertThat(ContactNormalizer.normalizeEmail("  Juan@Example.COM ")).isEqualTo("juan@example.com");
	}
}
