package com.ria.olita.tech.silingan.service.qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ria.olita.tech.silingan.exception.QrCodeGenerationException;
import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.extern.slf4j.Slf4j;

/**
 * ZXing-backed QR code renderer. Settings favour scanning reliability over branding.
 */
@Service
@Slf4j
public class QrCodeGeneratorImpl implements QrCodeGenerator {

	private static final int DEFAULT_SIZE = 512;
	private static final int MARGIN_MODULES = 2;
	private static final String PNG_FORMAT = "PNG";

	@Override
	public byte[] generatePng(String content) {
		if (content == null || content.isBlank()) {
			throw new ValidationException("QR code content is required");
		}

		Map<EncodeHintType, Object> hints = Map.of(
			EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
			EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
			EncodeHintType.MARGIN, MARGIN_MODULES
		);

		try {
			BitMatrix matrix = new QRCodeWriter()
				.encode(content, BarcodeFormat.QR_CODE, DEFAULT_SIZE, DEFAULT_SIZE, hints);

			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				MatrixToImageWriter.writeToStream(matrix, PNG_FORMAT, output);
				return output.toByteArray();
			}
		} catch (WriterException | IOException ex) {
			// The content is a join URL and is not logged: it carries the community code.
			log.error("QR code generation failed: {}", ex.getMessage());
			throw new QrCodeGenerationException("Unable to generate QR code");
		}
	}
}
