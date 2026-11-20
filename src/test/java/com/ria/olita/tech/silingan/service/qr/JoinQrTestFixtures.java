package com.ria.olita.tech.silingan.service.qr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.springframework.core.io.DefaultResourceLoader;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.ria.olita.tech.silingan.config.OnboardingProperties;

import java.util.Map;

/**
 * Shared wiring for the branded join QR tests: the real renderer, the real packaged brand asset and
 * the production defaults, so tests exercise exactly what the application ships.
 */
final class JoinQrTestFixtures {

	/** Shape-accurate stand-in for a real token: 32 random bytes as unpadded URL-safe Base64. */
	static final String SAMPLE_TOKEN = "qN4rT8wXzB2mK6vJ9pL3sD7fH1gY5cA0eU8iO2tR6xQ";

	private JoinQrTestFixtures() {
	}

	static OnboardingProperties properties() {
		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl("https://app.silingan.ph/join");
		return properties;
	}

	static BrandLogoProvider logoProvider(OnboardingProperties properties) {
		BrandLogoProvider provider = new BrandLogoProvider(new DefaultResourceLoader(), properties);
		provider.loadLogo();
		return provider;
	}

	static BrandedQrRenderer renderer(OnboardingProperties properties) {
		return new BrandedQrRenderer(properties, logoProvider(properties));
	}

	static QrCodeGenerator generator(OnboardingProperties properties) {
		return new QrCodeGeneratorImpl(properties, renderer(properties));
	}

	static QrCodeGenerator generator() {
		return generator(properties());
	}

	static BufferedImage read(byte[] png) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	static String decode(byte[] png) throws Exception {
		return decode(read(png));
	}

	/**
	 * Decodes with {@code TRY_HARDER}, matching how a camera scanner searches a full photographed
	 * card rather than a bare matrix.
	 */
	static String decode(BufferedImage image) throws Exception {
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		return new MultiFormatReader()
			.decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE))
			.getText();
	}
}
