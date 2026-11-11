package com.ria.olita.tech.silingan.dto.res;

/**
 * Derived dashboard conditions; they never replace the persisted community status. SINGLE_ADMIN is
 * intentionally absent: the domain allows at most one community administrator per community, so it
 * would flag every healthy community.
 */
public enum PlatformAttentionIssue {
	NO_ADMIN,
	INACTIVE_COMMUNITY,
	ADMIN_INVITATION_PENDING
}
