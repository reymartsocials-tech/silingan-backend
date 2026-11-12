package com.ria.olita.tech.silingan.service.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Hashes raw invitation tokens so only the digest is ever persisted or looked up.
 *
 * <p>Tokens have full 256-bit entropy, so a plain SHA-256 digest is sufficient: there is nothing to
 * brute-force and a slow KDF would only add cost to every scan.
 */
@Component
public class InvitationTokenHasher {

	private static final String ALGORITHM = "SHA-256";

	public String hash(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new IllegalArgumentException("Raw invitation token must not be blank");
		}

		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			byte[] result = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

			return HexFormat.of().formatHex(result);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
