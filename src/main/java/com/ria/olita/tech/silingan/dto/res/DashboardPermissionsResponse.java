package com.ria.olita.tech.silingan.dto.res;

/** Presentation hints only; the management endpoints enforce authorization independently. */
public record DashboardPermissionsResponse(
	boolean canManageResidents,
	boolean canManageStaff,
	boolean canCreateAnnouncement,
	boolean canViewReports,
	boolean canManageReports,
	boolean canManageDirectory
) {
}
