package com.ria.olita.tech.silingan.dto.res;

/** Presentation hints only; each target endpoint enforces its own authorization. */
public record PlatformDashboardPermissionsResponse(
	boolean canCreateCommunity,
	boolean canInviteAdministrator,
	boolean canViewCommunities,
	boolean canViewPendingInvitations
) {
}
