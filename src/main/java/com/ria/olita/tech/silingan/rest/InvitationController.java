package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.InvitationRequest;
import com.ria.olita.tech.silingan.dto.req.RevokeInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.InvitationSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.InvitationStatsResponse;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.service.InviteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/communities/{communityId}/invitations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Invitations", description = "Manage community invitations (staff and admin)")
public class InvitationController {

	private final InviteService inviteService;

	@PostMapping
	@Operation(
		summary = "Create an invitation (uniform for staff and admin)",
		description = "Create invitation with email, roleCode, and optional notes. " +
			"Invited user provides their profile details (name, mobile, address) during Keycloak's UPDATE_PROFILE required action. " +
			"Use roleCode='COMMUNITY_ADMIN' for admin invitations, or other role codes for staff invitations."
	)
	@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN') or hasAuthority('ROLE_COMMUNITY_ADMIN')")
	public ResponseEntity<ApiResponse<InvitationSummaryResponse>> createInvitation(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Valid @RequestBody InvitationRequest request
	) {
		InvitationSummaryResponse response = inviteService.invite(communityId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("Invitation created successfully", response));
	}

	@GetMapping
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "List invitations with pagination and filtering")
	public ResponseEntity<ApiResponse<Page<InvitationSummaryResponse>>> listInvitations(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Filter by invitation status")
		@RequestParam(required = false) InvitationStatus status,
		@Parameter(description = "Search by name, email, or position")
		@RequestParam(required = false) String search,
		@PageableDefault(size = 20, sort = "invitedAt", direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<InvitationSummaryResponse> invitations = inviteService.listInvitations(communityId, status, search, pageable);
		return ResponseEntity.ok(ApiResponse.success(invitations));
	}

	@GetMapping("/summary")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "Get invitation statistics for a community")
	public ResponseEntity<ApiResponse<InvitationStatsResponse>> getSummary(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		InvitationStatsResponse summary = inviteService.getSummary(communityId);
		return ResponseEntity.ok(ApiResponse.success(summary));
	}

	@PostMapping("/{invitationId}/revoke")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(summary = "Revoke a pending invitation")
	public ResponseEntity<ApiResponse<InvitationSummaryResponse>> revokeInvitation(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Invitation ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID invitationId,
		@Valid @RequestBody(required = false) RevokeInvitationRequest request
	) {
		String reason = request != null ? request.reason() : null;
		InvitationSummaryResponse response = inviteService.revokeInvitation(communityId, invitationId, reason);
		return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully", response));
	}
}
