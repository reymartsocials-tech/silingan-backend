package com.ria.olita.tech.silingan.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.UpsertIssueRoutingRequest;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingResponse;
import com.ria.olita.tech.silingan.dto.res.RoutingStaffSummaryResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.IssueRoutingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/issue-routing")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Issue Routing", description = "Community category-to-staff routing configuration")
public class IssueRoutingController {

	private final IssueRoutingService issueRoutingService;

	@GetMapping
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "Get issue routing configuration",
		description = "Returns every issue category of the community with its current routing destination.")
	@ApiResponse(responseCode = "200", description = "Routing configuration returned")
	public ResponseEntity<List<IssueRoutingResponse>> getRoutingConfiguration(
		@Parameter(description = "Community ID") @PathVariable UUID communityId) {
		return ResponseEntity.ok(issueRoutingService.getRoutingConfiguration(communityId));
	}

	@PutMapping("/{categoryId}")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(summary = "Create or replace a category route",
		description = "Routes a category to one eligible staff membership or to the PMO Review Queue. Existing reports are not reassigned.")
	@ApiResponse(responseCode = "200", description = "Routing saved")
	@ApiResponse(responseCode = "400", description = "Invalid destination payload")
	@ApiResponse(responseCode = "404", description = "Community, category, or staff membership not found")
	@ApiResponse(responseCode = "409", description = "Staff not eligible or concurrent update")
	public ResponseEntity<IssueRoutingResponse> upsertRouting(
		@Parameter(description = "Community ID") @PathVariable UUID communityId,
		@Parameter(description = "Issue category ID") @PathVariable UUID categoryId,
		@Valid @RequestBody UpsertIssueRoutingRequest request) {
		return ResponseEntity.ok(issueRoutingService.upsertRouting(communityId, categoryId, request));
	}

	@GetMapping("/eligible-staff")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "List staff eligible for routing",
		description = "Returns active staff memberships of the community that may handle reports.")
	@ApiResponse(responseCode = "200", description = "Eligible staff returned")
	public ResponseEntity<List<RoutingStaffSummaryResponse>> getEligibleStaff(
		@Parameter(description = "Community ID") @PathVariable UUID communityId,
		@Parameter(description = "Optional issue category ID") @RequestParam(required = false) UUID categoryId) {
		return ResponseEntity.ok(issueRoutingService.getEligibleStaff(communityId, categoryId));
	}
}
