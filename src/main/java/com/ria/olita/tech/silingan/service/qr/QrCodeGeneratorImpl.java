package com.ria.olita.tech.silingan.service.qr;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.QrCodeGenerationException;
import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ZXing-backed QR code renderer. Settings favour scanning reliability over branding.
 *
 * <p>Encoding and presentation are kept apart: this class only encodes the caller's content, and
 * {@link BrandedQrRenderer} composes the resulting matrix into the branded SILINGAN card. The
 * payload is therefore untouched by branding, and the finished PNG decodes to exactly the content
 * passed in.
 *
 * <p>The matrix is encoded at one pixel per module, quiet zone included, so the branded renderer can
 * scale it by a whole number of pixels per module instead of resampling a rasterised image.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeGeneratorImpl implements QrCodeGenerator {

	/** Asks ZXing for one pixel per module; the branded renderer applies the real scale. */
	private static final int MODULE_RESOLUTION = 1;

	private final OnboardingProperties onboardingProperties;
	private final BrandedQrRenderer brandedQrRenderer;

	@Override
	public byte[] generatePng(String content) {
		if (content == null || content.isBlank()) {
			throw new ValidationException("QR code content is required");
		}

		OnboardingProperties.Qr qr = onboardingProperties.getQr();

		// Level H: the centred brand mark covers part of the matrix, so the highest error correction
		// is what keeps the branded card scannable.
		Map<EncodeHintType, Object> hints = Map.of(
			EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
			EncodeHintType.ERROR_CORRECTION, qr.errorCorrectionLevel(),
			EncodeHintType.MARGIN, qr.getMarginModules()
		);

		try {
			BitMatrix matrix = new QRCodeWriter()
				.encode(content, BarcodeFormat.QR_CODE, MODULE_RESOLUTION, MODULE_RESOLUTION, hints);

			return brandedQrRenderer.renderPng(matrix);
		} catch (WriterException ex) {
			// The content is a join URL and is not logged: it carries the invitation token.
			log.error("QR code generation failed: {}", ex.getMessage());
			throw new QrCodeGenerationException("Unable to generate QR code");
		}
	}
}
