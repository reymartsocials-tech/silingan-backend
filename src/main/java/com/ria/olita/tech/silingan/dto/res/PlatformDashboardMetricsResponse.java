package com.ria.olita.tech.silingan.dto.res;

public record PlatformDashboardMetricsResponse(
	DashboardMetricResponse totalCommunities,
	DashboardMetricResponse totalResidents,
	DashboardMetricResponse totalAdmins,
	DashboardMetricResponse pendingInvitations
) {
}
