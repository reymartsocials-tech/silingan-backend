package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationCreatedResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Community Admin management of resident join invitations.
 *
 * <p>Distributing a join credential is a resident-onboarding management action, so every operation
 * requires {@code RESIDENT:MANAGE} rather than a read-only community permission. The {@code UUID}
 * {@code communityId} path variable also triggers the community scope aspect, which blocks
 * cross-community access.
 */
@RestController
@RequestMapping("/api/v1/communities/{communityId}/join-invitations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Community Join Invitations", description = "Create, list and revoke resident join QR invitations")
public class CommunityJoinInvitationController {

	private final CommunityJoinInvitationService communityJoinInvitationService;

	@PostMapping
	@RequiresPermission(domain = Domain.RESIDENT, action = Action.MANAGE)
	@Operation(
		summary = "Create a resident join invitation",
		description = "Generates a secure, expiring, revocable invitation token and returns the QR image "
			+ "exactly once. Only the token hash is stored, so the join target cannot be retrieved again: "
			+ "the administrator must download or share it immediately. To replace a lost invitation, "
			+ "revoke it and create a new one. The community is taken from the path and the creator from "
			+ "the authenticated principal.\n\n"
			+ "While `silingan.onboarding.invitation.link-distribution-enabled` is false the QR encodes a "
			+ "SILINGAN app deep link and `joinUrl` is null, so the invitation can only be used by scanning "
			+ "or uploading the QR inside the installed app. Enabling the property makes the QR encode the "
			+ "web join URL and returns the shareable link."
	)
	public ResponseEntity<ApiResponse<CommunityJoinInvitationCreatedResponse>> createInvitation(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Valid @RequestBody(required = false) CreateCommunityJoinInvitationRequest request
	) {
		CreateCommunityJoinInvitationRequest payload = request != null
			? request
			: CreateCommunityJoinInvitationRequest.builder().build();

		CommunityJoinInvitationCreatedResponse response = communityJoinInvitationService.create(communityId, payload);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("Community join invitation created", response));
	}

	@GetMapping
	@RequiresPermission(domain = Domain.RESIDENT, action = Action.MANAGE)
	@Operation(
		summary = "List resident join invitations",
		description = "Returns invitation metadata only, newest first. The raw token, token hash, join URL "
			+ "and QR image are never returned. Status reflects expiration and usage even when the stored "
			+ "status has not been updated yet."
	)
	public ResponseEntity<ApiResponse<Page<CommunityJoinInvitationResponse>>> listInvitations(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@PageableDefault(size = 20) Pageable pageable
	) {
		Page<CommunityJoinInvitationResponse> invitations = communityJoinInvitationService.list(communityId, pageable);
		return ResponseEntity.ok(ApiResponse.success(invitations));
	}

	@PostMapping("/{invitationId}/revoke")
	@RequiresPermission(domain = Domain.RESIDENT, action = Action.MANAGE)
	@Operation(
		summary = "Revoke a resident join invitation",
		description = "Immediately prevents any further preview or registration with the invitation. "
			+ "Idempotent: revoking an already revoked invitation succeeds. The record is retained for "
			+ "operational history."
	)
	public ResponseEntity<ApiResponse<CommunityJoinInvitationResponse>> revokeInvitation(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Invitation ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID invitationId
	) {
		CommunityJoinInvitationResponse response = communityJoinInvitationService.revoke(communityId, invitationId);
		return ResponseEntity.ok(ApiResponse.success("Community join invitation revoked", response));
	}
}
