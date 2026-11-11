package com.ria.olita.tech.silingan.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.PlatformGrowthPeriod;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardResponse;
import com.ria.olita.tech.silingan.service.PlatformDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/platform/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Platform Dashboard", description = "Platform-wide dashboard for Platform Admins")
public class PlatformDashboardController {

	private final PlatformDashboardService platformDashboardService;

	@GetMapping
	@Operation(
		summary = "Get the Platform Admin dashboard",
		description = "Requires the PLATFORM_ADMIN role; community administrators are rejected with 403. "
			+ "growthPeriod is 6_MONTHS (default) or 12_MONTHS; other values return 400. "
			+ "communityGrowth lists communities created per calendar month (current month included, zero months "
			+ "included). A metric with percentageChange null has comparisonAvailable=false (show N/A): only "
			+ "pendingInvitations has retained history. communitiesByStatus groups are mutually exclusive: "
			+ "inactive = ARCHIVE, or DRAFT already having an administrator; needsAdmin = non-archived without an "
			+ "active administrator; active = ACTIVE with one. Attention issues: NO_ADMIN, INACTIVE_COMMUNITY, "
			+ "ADMIN_INVITATION_PENDING (issue is the first of issues). recentActivities are derived from community "
			+ "and administrator-invitation timestamps."
	)
	public ResponseEntity<ApiResponse<PlatformDashboardResponse>> getDashboard(
		@Parameter(description = "Community growth window", example = "6_MONTHS")
		@RequestParam(defaultValue = PlatformGrowthPeriod.DEFAULT_VALUE) String growthPeriod
	) {
		return ResponseEntity.ok(ApiResponse.success(
			platformDashboardService.getDashboard(PlatformGrowthPeriod.fromValue(growthPeriod))));
	}
}
