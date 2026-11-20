package com.ria.olita.tech.silingan.service.qr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.QrCodeGenerationException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the packaged SILINGAN brand mark once and hands the decoded image to the QR renderer.
 *
 * <p>The asset is a static public brand file shipped on the classpath, so it is safe to cache. The
 * location comes from configuration and is never request-controlled, and nothing is ever fetched
 * over the network: an invitation image can only ever carry the official SILINGAN mark.
 *
 * <p>A missing, unreadable or corrupt asset fails startup rather than silently producing an
 * unbranded or wrongly branded invitation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BrandLogoProvider {

	private final ResourceLoader resourceLoader;
	private final OnboardingProperties onboardingProperties;

	private BufferedImage logo;

	/**
	 * @return the decoded brand mark. Callers must treat it as immutable and draw it rather than
	 * mutate it, because the same instance is shared by every invitation.
	 */
	public BufferedImage logo() {
		return logo;
	}

	@PostConstruct
	void loadLogo() {
		String location = onboardingProperties.getQr().getLogoResource();
		Resource resource = resourceLoader.getResource(location);

		if (!resource.exists()) {
			throw new QrCodeGenerationException("SILINGAN branding resource is not available");
		}

		BufferedImage image;
		try (InputStream input = resource.getInputStream()) {
			image = ImageIO.read(input);
		} catch (IOException ex) {
			// Only the branding failure is reported: no invitation data is involved here.
			log.error("SILINGAN branding resource could not be read: {}", ex.getMessage());
			throw new QrCodeGenerationException("SILINGAN branding resource could not be read");
		}

		if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
			throw new QrCodeGenerationException("SILINGAN branding resource is not a supported image");
		}

		logo = image;
		log.info("Loaded SILINGAN branding resource ({}x{})", image.getWidth(), image.getHeight());
	}
}
