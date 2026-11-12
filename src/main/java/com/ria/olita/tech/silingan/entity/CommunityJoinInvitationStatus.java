package com.ria.olita.tech.silingan.entity;

/**
 * Lifecycle state of a community join invitation.
 *
 * <p>The stored value is authoritative only for {@link #REVOKED}. Expiration and exhaustion are
 * always re-evaluated against the current time and usage, so validation never depends on a
 * scheduled job having run.
 */
public enum CommunityJoinInvitationStatus {
	ACTIVE,
	REVOKED,
	EXHAUSTED,
	EXPIRED
}
