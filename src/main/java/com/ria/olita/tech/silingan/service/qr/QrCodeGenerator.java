package com.ria.olita.tech.silingan.service.qr;

/**
 * Renders text content as a QR code image.
 */
public interface QrCodeGenerator {

	/**
	 * @return PNG bytes encoding the given content.
	 */
	byte[] generatePng(String content);
}
