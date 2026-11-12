package com.ria.olita.tech.silingan.service.invitation;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Generates the raw secret carried by a community join QR code or link.
 *
 * <p>The token is 32 cryptographically random bytes (256 bits of entropy) rendered as unpadded
 * URL-safe Base64, so it is safe to place in a query string and is not guessable, enumerable or
 * derivable from any community attribute.
 */
@Component
public class SecureInvitationTokenGenerator {

	static final int TOKEN_BYTE_LENGTH = 32;

	/** Unpadded URL-safe Base64 of 32 bytes. */
	public static final int TOKEN_LENGTH = 43;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);

		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(bytes);
	}
}
