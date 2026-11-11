package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.CreateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.UpdateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.res.DirectoryContactResponse;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.DirectoryContactService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Staff management of the community directory. The annotation guards the directory permission;
 * the service additionally verifies that the caller has an active membership in the community
 * owning each contact.
 */
@RestController
@RequestMapping("/api/v1/directory-contacts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Directory Contact", description = "Community directory and emergency contact management APIs")
public class DirectoryContactController {

	private final DirectoryContactService directoryContactService;

	@PostMapping
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.MANAGE)
	@Operation(summary = "Create a directory contact for a community the caller manages")
	public ResponseEntity<DirectoryContactResponse> createContact(
		@Valid @RequestBody CreateDirectoryContactRequest request
	) {
		return new ResponseEntity<>(directoryContactService.createContact(request), HttpStatus.CREATED);
	}

	@GetMapping
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.VIEW)
	@Operation(
		summary = "Get directory contacts of the caller's community",
		description = "Returns active and inactive contacts. Emergency contacts are ordered first, "
			+ "then display order, display name and contact ID."
	)
	public ResponseEntity<Page<DirectoryContactResponse>> getContacts(
		@Parameter(description = "Community ID. Defaults to the caller's active community.", example = "550e8400-e29b-41d4-a716-446655440000")
		@RequestParam(required = false) UUID communityId,
		@Parameter(description = "Filter by category", example = "EMERGENCY_SERVICE")
		@RequestParam(required = false) DirectoryContactCategory category,
		@Parameter(description = "Filter by contact type", example = "POLICE")
		@RequestParam(required = false) DirectoryContactType contactType,
		@Parameter(description = "Filter by active status")
		@RequestParam(required = false) Boolean active,
		@Parameter(description = "Search keyword matched against the display name")
		@RequestParam(required = false) String keyword,
		Pageable pageable
	) {
		DirectoryContactFilterCriteria criteria = DirectoryContactFilterCriteria.builder()
			.category(category)
			.contactType(contactType)
			.active(active)
			.keyword(keyword)
			.build();

		return ResponseEntity.ok(directoryContactService.getContacts(communityId, criteria, pageable));
	}

	@GetMapping("/{contactId}")
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.VIEW)
	@Operation(summary = "Get a directory contact of the caller's community")
	public ResponseEntity<DirectoryContactResponse> getContactById(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId
	) {
		return ResponseEntity.ok(directoryContactService.getContactById(contactId));
	}

	@PutMapping("/{contactId}")
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.MANAGE)
	@Operation(summary = "Update a directory contact. The owning community cannot be changed.")
	public ResponseEntity<DirectoryContactResponse> updateContact(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId,
		@Valid @RequestBody UpdateDirectoryContactRequest request
	) {
		return ResponseEntity.ok(directoryContactService.updateContact(contactId, request));
	}

	@PutMapping("/{contactId}/activate")
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.MANAGE)
	@Operation(summary = "Make a directory contact visible to residents")
	public ResponseEntity<DirectoryContactResponse> activateContact(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId
	) {
		return ResponseEntity.ok(directoryContactService.activateContact(contactId));
	}

	@PutMapping("/{contactId}/deactivate")
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.MANAGE)
	@Operation(summary = "Hide a directory contact from residents while keeping it for staff")
	public ResponseEntity<DirectoryContactResponse> deactivateContact(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId
	) {
		return ResponseEntity.ok(directoryContactService.deactivateContact(contactId));
	}

	@DeleteMapping("/{contactId}")
	@RequiresPermission(domain = Domain.DIRECTORY, action = Action.MANAGE)
	@Operation(summary = "Soft delete a directory contact of the caller's community")
	public ResponseEntity<Void> deleteContact(
		@Parameter(description = "Directory contact ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID contactId
	) {
		directoryContactService.deleteContact(contactId);
		return ResponseEntity.noContent().build();
	}
}
