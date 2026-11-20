package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.QrCodeGenerationException;

/**
 * The brand mark is packaged with the application, so an unusable asset must surface at startup
 * rather than produce a wrongly branded invitation at request time.
 */
class BrandLogoProviderTest {

	/** Bounding box of the supplied official mark, used to prove its proportions are kept. */
	private static final double SUPPLIED_ASPECT_RATIO = 1805d / 1644d;

	private final OnboardingProperties properties = JoinQrTestFixtures.properties();

	private BrandLogoProvider provider() {
		return new BrandLogoProvider(new DefaultResourceLoader(), properties);
	}

	@Test
	void loadsThePackagedSilinganMark() {
		BrandLogoProvider provider = provider();
		provider.loadLogo();

		BufferedImage logo = provider.logo();
		assertThat(logo).isNotNull();
		assertThat(logo.getWidth()).isPositive();
		assertThat(logo.getHeight()).isPositive();
	}

	@Test
	void keepsTheSuppliedMarkTransparentOutsideItsStrokes() {
		BrandLogoProvider provider = provider();
		provider.loadLogo();

		BufferedImage logo = provider.logo();

		assertThat(logo.getColorModel().hasAlpha()).isTrue();
		// The mark is an open house outline: its top-left corner is empty.
		assertThat(new java.awt.Color(logo.getRGB(0, 0), true).getAlpha()).isZero();
	}

	@Test
	void keepsTheSuppliedMarkProportions() {
		BrandLogoProvider provider = provider();
		provider.loadLogo();

		BufferedImage logo = provider.logo();
		double ratio = logo.getWidth() / (double) logo.getHeight();

		assertThat(ratio).isCloseTo(SUPPLIED_ASPECT_RATIO, org.assertj.core.data.Percentage.withPercentage(1));
	}

	@Test
	void cachesTheDecodedMarkInsteadOfRereadingItPerInvitation() {
		BrandLogoProvider provider = provider();
		provider.loadLogo();

		assertThat(provider.logo()).isSameAs(provider.logo());
	}

	@Test
	void failsWhenTheBrandingResourceIsMissing() {
		properties.getQr().setLogoResource("classpath:branding/does-not-exist.png");

		assertThatThrownBy(() -> provider().loadLogo())
			.isInstanceOf(QrCodeGenerationException.class)
			.hasMessageContaining("branding resource");
	}

	@Test
	void failsWhenTheBrandingResourceIsNotASupportedImage(@TempDir Path tempDir) throws Exception {
		Path corrupt = tempDir.resolve("silingan-icon.png");
		Files.writeString(corrupt, "not an image");
		properties.getQr().setLogoResource(corrupt.toUri().toString());

		assertThatThrownBy(() -> provider().loadLogo())
			.isInstanceOf(QrCodeGenerationException.class)
			.hasMessageContaining("branding resource");
	}
}
