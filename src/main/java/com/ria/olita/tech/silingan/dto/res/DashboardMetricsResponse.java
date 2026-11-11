package com.ria.olita.tech.silingan.dto.res;

/** Each metric is null when the caller lacks view access to the module it is derived from. */
public record DashboardMetricsResponse(
	DashboardMetricResponse activeResidents,
	DashboardMetricResponse openReports,
	DashboardMetricResponse announcementsPublishedThisMonth,
	DashboardMetricResponse activeCommunityStaff
) {
}
