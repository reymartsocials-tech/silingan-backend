package com.ria.olita.tech.silingan.dto.res;

import java.util.List;

/** Sections the caller may not view are returned empty (lists) or null (metrics) and are never queried. */
public record CommunityDashboardResponse(
	DashboardMetricsResponse metrics,
	List<DashboardAnnouncementResponse> recentAnnouncements,
	List<DashboardReportStatusResponse> reportStatus,
	List<DashboardReportResponse> recentReports,
	List<DashboardContactResponse> directoryContacts,
	DashboardPermissionsResponse permissions
) {
}
