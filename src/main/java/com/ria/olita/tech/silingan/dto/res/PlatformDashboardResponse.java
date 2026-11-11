package com.ria.olita.tech.silingan.dto.res;

import java.util.List;

public record PlatformDashboardResponse(
	PlatformDashboardMetricsResponse metrics,
	List<PlatformGrowthPointResponse> communityGrowth,
	PlatformCommunityStatusSummaryResponse communitiesByStatus,
	List<PlatformActivityResponse> recentActivities,
	List<PlatformAttentionCommunityResponse> communitiesNeedingAttention,
	PlatformDashboardPermissionsResponse permissions
) {
}
