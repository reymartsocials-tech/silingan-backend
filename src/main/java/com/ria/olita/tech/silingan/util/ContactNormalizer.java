package com.ria.olita.tech.silingan.util;

/**
 * Canonicalises user contact details so that uniqueness checks compare like with like.
 *
 * <p>Without this, {@code 09171234567} and {@code +639171234567} are the same subscriber but two
 * distinct strings, and {@code Juan@Example.com} would bypass an email uniqueness check.
 */
public final class ContactNormalizer {

	private static final String PH_COUNTRY_CODE = "+63";

	private ContactNormalizer() {
	}

	/**
	 * Lower-cases and trims the email. Blank input is normalised to {@code null} so that multiple
	 * users without an email do not collide on a unique constraint.
	 */
	public static String normalizeEmail(String email) {
		if (email == null) {
			return null;
		}
		String trimmed = email.trim().toLowerCase();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Converts a Philippine mobile number to canonical {@code +639XXXXXXXXX} form.
	 *
	 * <p>Separators are stripped first so that {@code 0917-123-4567} and {@code +639171234567}
	 * resolve to the same stored value; without that, a uniqueness check is trivially bypassed and
	 * OTP login lookups miss.
	 */
	public static String normalizeMobileNumber(String mobileNumber) {
		if (mobileNumber == null) {
			return null;
		}

		String cleaned = mobileNumber.replaceAll("[\\s()\\-.]", "").trim();
		if (cleaned.isEmpty()) {
			return null;
		}

		if (cleaned.startsWith("+")) {
			return cleaned;
		}
		if (cleaned.startsWith("0")) {
			return PH_COUNTRY_CODE + cleaned.substring(1);
		}
		return "+" + cleaned;
	}
}
