package com.ria.olita.tech.silingan.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleResponse;
import com.ria.olita.tech.silingan.service.CommunityRbacService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only catalogue of the predefined staff roles and the permission matrix.
 *
 * <p>For MVP custom roles and custom permissions are not supported, so this controller exposes no
 * write operations.
 */
@RestController
@RequestMapping("/api/v1/communities/{communityId}/roles")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Role & Permission System", description = "Predefined staff roles and their permission matrix (read-only)")
public class StaffRoleCatalogController {

	private final CommunityRbacService communityRbacService;

	@GetMapping
	@Operation(
		summary = "Get predefined role catalog",
		description = "Returns the predefined staff roles with name, description, highest-access flag, and granted permissions."
	)
	@ApiResponse(responseCode = "200", description = "Predefined roles returned")
	@ApiResponse(responseCode = "404", description = "Community not found")
	public ResponseEntity<List<StaffRoleResponse>> getRoleCatalog(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		return ResponseEntity.ok(communityRbacService.getRoleCatalog(communityId));
	}

	@GetMapping("/permissions")
	@Operation(
		summary = "Get permission matrix",
		description = "Returns the permission matrix with roles as columns and modules as rows. Each cell is VIEW_AND_MANAGE, VIEW_ONLY, or NO_ACCESS."
	)
	@ApiResponse(responseCode = "200", description = "Permission matrix returned")
	@ApiResponse(responseCode = "404", description = "Community not found")
	public ResponseEntity<PermissionMatrixResponse> getPermissionMatrix(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		return ResponseEntity.ok(communityRbacService.getPermissionMatrix(communityId));
	}
}
