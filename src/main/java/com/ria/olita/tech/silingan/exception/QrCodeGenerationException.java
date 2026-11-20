package com.ria.olita.tech.silingan.exception;

public class QrCodeGenerationException extends ServiceException {

	public QrCodeGenerationException(String message) {
		super("QR_GENERATION_FAILED", message);
	}
}
