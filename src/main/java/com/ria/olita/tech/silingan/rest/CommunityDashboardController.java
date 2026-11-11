package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityDashboardResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.CommunityDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Community Dashboard", description = "Aggregated, community-scoped dashboard for community staff")
public class CommunityDashboardController {

	private final CommunityDashboardService communityDashboardService;

	@GetMapping
	@RequiresPermission(domain = Domain.COMMUNITY, action = Action.VIEW)
	@Operation(
		summary = "Get the community dashboard",
		description = "Requires community:view and membership of the community. Sections for modules the caller "
			+ "cannot view are returned empty (metrics: null). A metric whose percentageChange is null has "
			+ "comparisonAvailable=false and should be shown as N/A: the previous period is zero or cannot be "
			+ "reconstructed because the domain keeps no history for it."
	)
	public ResponseEntity<ApiResponse<CommunityDashboardResponse>> getDashboard(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		return ResponseEntity.ok(ApiResponse.success(communityDashboardService.getDashboard(communityId)));
	}
}
