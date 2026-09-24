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

import com.ria.olita.tech.silingan.dto.req.CreateStaffInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;
import com.ria.olita.tech.silingan.service.StaffInvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/communities/{communityId}/staff-invitations")
@RequiredArgsConstructor
@Tag(name = "Staff Invitations", description = "Manage staff member invitations")
public class StaffInvitationController {

	private final StaffInvitationService staffInvitationService;

	@PostMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Create a new staff invitation")
	public ResponseEntity<StaffInvitationResponse> createInvitation(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Valid @RequestBody CreateStaffInvitationRequest request
	) {
		StaffInvitationResponse response = staffInvitationService.createInvitation(communityId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "List staff invitations with pagination and filtering")
	public ResponseEntity<Page<StaffInvitationResponse>> listInvitations(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Filter by invitation status")
		@RequestParam(required = false) StaffInvitationStatus status,
		@Parameter(description = "Search by name, email, or position")
		@RequestParam(required = false) String search,
		@PageableDefault(size = 20, sort = "invitedAt", direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<StaffInvitationResponse> page = staffInvitationService.listInvitations(communityId, status, search, pageable);
		return ResponseEntity.ok(page);
	}

	@GetMapping("/summary")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get invitation statistics for a community")
	public ResponseEntity<StaffInvitationSummaryResponse> getSummary(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		StaffInvitationSummaryResponse summary = staffInvitationService.getSummary(communityId);
		return ResponseEntity.ok(summary);
	}
}
