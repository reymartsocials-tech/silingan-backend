package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.AssignStaffRoleRequest;
import com.ria.olita.tech.silingan.dto.res.EffectivePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleAssignmentResponse;
import com.ria.olita.tech.silingan.service.CommunityRbacService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMUNITY_ADMIN') or hasRole('PLATFORM_ADMIN')")
@Tag(name = "Staff Role Assignment", description = "Assign community staff roles and inspect effective access")
public class StaffRoleAssignmentController {

	private final CommunityRbacService communityRbacService;

	@PutMapping("/{userId}/role")
	@Operation(summary = "Assign or replace the staff role for a community member")
	public ResponseEntity<StaffRoleAssignmentResponse> assignStaffRole(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID userId,
		@Valid @RequestBody AssignStaffRoleRequest request
	) {
		return ResponseEntity.ok(communityRbacService.assignStaffRole(communityId, userId, request));
	}

	@GetMapping("/{userId}/role")
	@Operation(summary = "Get the assigned staff role for a community member")
	public ResponseEntity<StaffRoleAssignmentResponse> getStaffRoleAssignment(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID userId
	) {
		return ResponseEntity.ok(communityRbacService.getStaffRoleAssignment(communityId, userId));
	}

	@GetMapping("/{userId}/effective-permissions")
	@Operation(summary = "Get effective permissions for a community member")
	public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID userId
	) {
		return ResponseEntity.ok(communityRbacService.getEffectivePermissions(communityId, userId));
	}
}
