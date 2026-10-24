package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.AssignStaffRoleRequest;
import com.ria.olita.tech.silingan.dto.req.CreateStaffAccountRequest;
import com.ria.olita.tech.silingan.dto.req.EditStaffInfoRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.dto.res.EffectivePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.StaffAccountResponse;
import com.ria.olita.tech.silingan.dto.res.StaffDetailsResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleAssignmentResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.CommunityRbacService;
import com.ria.olita.tech.silingan.service.StaffAccountService;
import com.ria.olita.tech.silingan.service.StaffDirectoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Consolidated staff management controller.
 * All staff-related operations: view, create, edit, assign roles, and activate/deactivate.
 */
@RestController
@RequestMapping("/api/v1/communities/{communityId}/staff")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Staff Management", description = "Complete staff account lifecycle management")
public class StaffManagementController {

	private final StaffAccountService staffAccountService;
	private final StaffDirectoryService staffDirectoryService;
	private final CommunityRbacService communityRbacService;

	@GetMapping("/directory")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(
		summary = "List community staff directory",
		description = "Get paginated staff list with optional search by name/email/mobile, role filter, and status filter."
	)
	@ApiResponse(responseCode = "200", description = "Staff directory returned")
	public ResponseEntity<Page<CommunityStaffMemberResponse>> getCommunityStaffDirectory(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Search by name, email, or mobile", example = "juan")
		@RequestParam(required = false) String searchTerm,
		@Parameter(description = "Filter by role", example = "PMO_STAFF")
		@RequestParam(required = false) StaffRoleCode role,
		@Parameter(description = "Filter by status", example = "ACTIVE")
		@RequestParam(required = false) CommunityStaffStatus status,
		Pageable pageable) {
		return ResponseEntity.ok(staffDirectoryService.getCommunityStaffDirectory(communityId, searchTerm, role, status, pageable));
	}

	@GetMapping("/{staffUserId}")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "Get staff member details")
	@ApiResponse(responseCode = "200", description = "Staff details returned")
	@ApiResponse(responseCode = "404", description = "Staff not found")
	public ResponseEntity<StaffDetailsResponse> getStaffMember(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId) {
		return ResponseEntity.ok(staffDirectoryService.getStaffMember(communityId, staffUserId));
	}

	@PostMapping("/accounts")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(
		summary = "Create staff account directly",
		description = "Create staff account without invitation. Auto-generates temporary password. Account created with ACTIVE status."
	)
	@ApiResponse(responseCode = "201", description = "Staff account created")
	public ResponseEntity<StaffAccountResponse> createStaffAccount(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Valid @RequestBody CreateStaffAccountRequest request
	) {
		StaffAccountResponse response = staffAccountService.createStaffAccount(communityId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{staffUserId}/info")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(
		summary = "Edit staff personal information",
		description = "Update staff member's first name, last name, and mobile number. " +
			"Email and staff ID are not editable. Role changes use the separate role assignment endpoint."
	)
	public ResponseEntity<StaffAccountResponse> editStaffInfo(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId,
		@Valid @RequestBody EditStaffInfoRequest request
	) {
		StaffAccountResponse response = staffAccountService.editStaffInfo(communityId, staffUserId, request);
		return ResponseEntity.ok(response);
	}

	@PutMapping("/{staffUserId}/role")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(
		summary = "Assign or change staff role",
		description = "Assign a predefined staff role to a community member. Replaces existing role if any."
	)
	public ResponseEntity<StaffRoleAssignmentResponse> assignStaffRole(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId,
		@Valid @RequestBody AssignStaffRoleRequest request
	) {
		return ResponseEntity.ok(communityRbacService.assignStaffRole(communityId, staffUserId, request));
	}

	@GetMapping("/{staffUserId}/role")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "Get staff member's assigned role")
	public ResponseEntity<StaffRoleAssignmentResponse> getStaffRoleAssignment(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId
	) {
		return ResponseEntity.ok(communityRbacService.getStaffRoleAssignment(communityId, staffUserId));
	}

	@GetMapping("/{staffUserId}/effective-permissions")
	@RequiresPermission(domain = Domain.STAFF, action = Action.VIEW)
	@Operation(summary = "Get staff member's effective permissions")
	public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId
	) {
		return ResponseEntity.ok(communityRbacService.getEffectivePermissions(communityId, staffUserId));
	}

	@PutMapping("/{staffUserId}/deactivate")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(
		summary = "Deactivate staff account",
		description = "Set staff status to INACTIVE. Inactive staff cannot access system. Can be reactivated."
	)
	public ResponseEntity<StaffAccountResponse> deactivateStaff(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId
	) {
		StaffAccountResponse response = staffAccountService.deactivateStaff(communityId, staffUserId);
		return ResponseEntity.ok(response);
	}

	@PutMapping("/{staffUserId}/activate")
	@RequiresPermission(domain = Domain.STAFF, action = Action.MANAGE)
	@Operation(
		summary = "Activate staff account",
		description = "Set staff status to ACTIVE. Allows previously deactivated staff to access system."
	)
	public ResponseEntity<StaffAccountResponse> activateStaff(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId,
		@Parameter(description = "Staff user ID", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID staffUserId
	) {
		StaffAccountResponse response = staffAccountService.activateStaff(communityId, staffUserId);
		return ResponseEntity.ok(response);
	}
}
