package com.ria.olita.tech.silingan.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentDirectoryContactResponse;
import com.ria.olita.tech.silingan.service.DirectoryContactService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Resident-facing, read-only view over the community directory. The community is always derived
 * from the authenticated resident's active membership, so no community identifier is accepted from
 * the client. Only active contacts are returned.
 */
@RestController
@RequestMapping("/api/v1/secure/directory-contacts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated() && hasRole('RESIDENT')")
@Tag(name = "Resident Directory Contact", description = "Community directory and emergency contact APIs for residents")
public class SecuredDirectoryContactController {

	private final DirectoryContactService directoryContactService;

	@GetMapping
	@Operation(
		summary = "Get active directory contacts of the authenticated resident's active community",
		description = "Emergency contacts are returned first, followed by community services, "
			+ "utility providers and other services."
	)
	public ResponseEntity<ApiResponse<List<ResidentDirectoryContactResponse>>> getContacts() {
		return ResponseEntity.ok(ApiResponse.success(directoryContactService.getContactsForResident()));
	}

	@GetMapping("/{contactId}")
	@Operation(summary = "Get an active directory contact of the authenticated resident's active community")
	public ResponseEntity<ApiResponse<ResidentDirectoryContactResponse>> getContact(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId
	) {
		return ResponseEntity.ok(ApiResponse.success(directoryContactService.getContactForResident(contactId)));
	}
}
