package com.ria.olita.tech.silingan.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.service.StaffDirectoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMUNITY_ADMIN') or hasRole('PLATFORM_ADMIN')")
@Tag(name = "Staff Directory", description = "Centralized staff directory for a community")
public class StaffDirectoryController {

	private final StaffDirectoryService staffDirectoryService;

	@GetMapping("/directory")
	@Operation(
		summary = "Get community staff directory",
		description = "Returns the community staff with optional search, role filter, and status filter."
	)
	@ApiResponse(responseCode = "200", description = "Staff directory returned")
	@ApiResponse(responseCode = "404", description = "Community not found")
	public ResponseEntity<List<CommunityStaffMemberResponse>> getCommunityStaffDirectory(
			@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
			@PathVariable UUID communityId,
			@Parameter(description = "Search by full name, email, or mobile number", example = "juan")
			@RequestParam(required = false) String searchTerm,
			@Parameter(description = "Optional role filter", example = "PMO_STAFF")
			@RequestParam(required = false) StaffRoleCode role,
			@Parameter(description = "Optional status filter", example = "ACTIVE")
			@RequestParam(required = false) CommunityStaffStatus status) {
		return ResponseEntity.ok(staffDirectoryService.getCommunityStaffDirectory(communityId, searchTerm, role, status));
	}
}
