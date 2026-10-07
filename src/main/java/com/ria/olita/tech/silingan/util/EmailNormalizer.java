package com.ria.olita.tech.silingan.util;

import java.util.Locale;

/**
 * Canonical form for emails used as lookup keys.
 *
 * <p>Invitation lookups match on {@code LOWER(email)} so that addresses are compared
 * case-insensitively. Lower-casing the <em>parameter</em> here rather than wrapping it in SQL is
 * what lets Postgres match the {@code LOWER(email)} partial indexes; a {@code LOWER(?)} on the
 * right-hand side leaves the planner with an unindexable predicate.
 */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	/**
	 * @return the trimmed, lower-cased address, or {@code null} when blank or absent. Null is
	 *         meaningful to callers: it signals "no email identity to match on".
	 */
	public static String normalize(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
