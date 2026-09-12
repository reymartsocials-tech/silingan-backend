package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.AvailablePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.CurrentUserCapabilitiesResponse;
import com.ria.olita.tech.silingan.service.CommunityRbacService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Community RBAC", description = "Permission definitions and caller capabilities")
public class CommunityRbacController {

	private final CommunityRbacService communityRbacService;

	@GetMapping("/rbac/permissions")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get all permission definitions")
	public ResponseEntity<AvailablePermissionsResponse> getPermissionCatalog() {
		return ResponseEntity.ok(communityRbacService.getPermissionCatalog());
	}

	@GetMapping("/me/communities/{communityId}/capabilities")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get current user capabilities for a community")
	public ResponseEntity<CurrentUserCapabilitiesResponse> getCurrentUserCapabilities(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID communityId
	) {
		return ResponseEntity.ok(communityRbacService.getCurrentUserCapabilities(communityId));
	}
}
