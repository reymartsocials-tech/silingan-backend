package com.ria.olita.tech.silingan.domain;

import java.util.UUID;
import java.util.Objects;

/**
 * Domain identifier for Community. This is a value object that wraps a UUID
 * and provides type-safe access to community IDs throughout the application.
 *
 * Using this type instead of raw UUIDs allows the security layer to reliably
 * detect community-scoped parameters regardless of their naming convention.
 */
public class CommunityId {

	private final UUID value;

	public CommunityId(UUID value) {
		Objects.requireNonNull(value, "CommunityId value cannot be null");
		this.value = value;
	}

	public static CommunityId of(UUID value) {
		return new CommunityId(value);
	}

	public static CommunityId of(String value) {
		return new CommunityId(UUID.fromString(value));
	}

	public UUID getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CommunityId that = (CommunityId) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
