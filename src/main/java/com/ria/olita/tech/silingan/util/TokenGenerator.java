package com.ria.olita.tech.silingan.util;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Generates secure random tokens for invitation links.
 * Uses SecureRandom with Base64 URL-safe encoding.
 */
@Component
public class TokenGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32; // 256 bits

	/**
	 * Generate a secure random token suitable for invitation links.
	 *
	 * @return a URL-safe Base64 encoded token
	 */
	public String generateSecureToken() {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}
}
