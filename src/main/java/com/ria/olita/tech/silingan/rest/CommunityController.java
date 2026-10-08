package com.ria.olita.tech.silingan.rest;

import com.ria.olita.tech.silingan.dto.req.CommunityStatusUpdateRequest;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateCommunityRequest;
import com.ria.olita.tech.silingan.domain.CommunityId;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;
import com.ria.olita.tech.silingan.service.CommunityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.DefaultValue;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/communities")
@RequiredArgsConstructor
@Tag(name = "Community", description = "Community management APIs")
public class CommunityController {

	private final CommunityService communityService;

	@PostMapping
	@Operation(
		summary = "Create a new community",
		requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			content = @Content(
				schema = @Schema(implementation = CreateCommunityRequest.class),
				examples = {
					@ExampleObject(
						name = "createCommunityExample",
						summary = "Example community creation (condo)",
						value = """
							{
								"name": "Greenbelt Residences",
								"type": "CONDO",
								"address": {
									"street": "123 Main St",
									"barangay": "Poblacion",
									"city": "Makati",
									"province": "Metro Manila",
									"region": 13,
									"postalCode": "1200",
									"country": "Philippines",
									"buildingName": "Greenbelt Tower",
									"tower": "Tower 1",
									"unitNumber": "Unit 1205",
									"floor": "12"
								},
								"tenantId": "550e8400-e29b-41d4-a716-446655440000"
							}
							"""
					)
				}
			)
		)
	)
	public ResponseEntity<ApiResponse<CommunityResponse>> create(
		@Valid @RequestBody CreateCommunityRequest request) {
		CommunityResponse response = communityService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("Community created successfully", response));
	}

	@GetMapping("/{communityId}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get community by ID")
	public ResponseEntity<ApiResponse<CommunityResponse>> getById(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable CommunityId communityId) {
		CommunityResponse response = communityService.getById(communityId.getValue());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@GetMapping("/code/{code}")
	@Operation(summary = "Get community by code")
	public ResponseEntity<ApiResponse<CommunityResponse>> getByCode(
		@Parameter(description = "Community code", example = "GR-001") @PathVariable String code) {
		CommunityResponse response = communityService.getByCode(code);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(summary = "Get all communities")
	public ResponseEntity<ApiResponse<List<CommunityResponse>>> getAll() {
		List<CommunityResponse> responses = communityService.getAll();
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@GetMapping("/status/{status}")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(summary = "Get communities by status")
	public ResponseEntity<ApiResponse<List<CommunityResponse>>> getByStatus(
		@Parameter(description = "Community status", example = "ACTIVE") @PathVariable CommunityStatus status) {
		List<CommunityResponse> responses = communityService.getByStatus(status);
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@GetMapping("/type/{type}")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(summary = "Get communities by type")
	public ResponseEntity<ApiResponse<List<CommunityResponse>>> getByType(
		@Parameter(description = "Community type", example = "CONDO") @PathVariable CommunityType type) {
		List<CommunityResponse> responses = communityService.getByType(type);
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@PutMapping("/{communityId}")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(
		summary = "Update an existing community",
		requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			content = @Content(
				schema = @Schema(implementation = UpdateCommunityRequest.class),
				examples = {
					@ExampleObject(
						name = "updateCommunityExample",
						summary = "Example community update",
						value = """
							{
								"name": "Greenbelt Residences",
								"code": "GR-001",
								"type": "CONDO",
								"address": {
									"street": "123 Main St",
									"barangay": "Poblacion",
									"city": "Makati",
									"province": "Metro Manila",
									"region": 13,
									"postalCode": "1200",
									"country": "Philippines"
								},
								"latitude": 14.5547,
								"longitude": 121.0244,
								"status": "ACTIVE"
							}
							"""
					)
				}
			)
		)
	)
	public ResponseEntity<ApiResponse<CommunityResponse>> update(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable CommunityId communityId,
		@RequestBody UpdateCommunityRequest request) {
		CommunityResponse response = communityService.update(communityId.getValue(), request);
		return ResponseEntity.ok(ApiResponse.success("Community updated successfully", response));
	}

	@DeleteMapping("/{communityId}")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(summary = "Delete a community")
	public ResponseEntity<ApiResponse<Void>> delete(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable CommunityId communityId) {
		communityService.delete(communityId.getValue());
		return ResponseEntity.ok(ApiResponse.success("Community deleted successfully", null));
	}

	@PutMapping("/{communityId}/status")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(
		summary = "Update community status",
		requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			content = @Content(
				schema = @Schema(implementation = CommunityStatusUpdateRequest.class),
				examples = {
					@ExampleObject(
						name = "updateCommunityStatusExample",
						summary = "Example community status update",
						value = """
							{
								"status": "ACTIVE"
							}
							"""
					)
				}
			)
		)
	)
	public ResponseEntity<ApiResponse<Void>> updateStatus(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable("communityId") CommunityId communityId,
		@Valid @RequestBody CommunityStatusUpdateRequest communityStatusUpdateRequest) {
		communityService.updateStatus(communityId.getValue(), communityStatusUpdateRequest.status());
		return ResponseEntity.ok(ApiResponse.success("Community status updated successfully", null));
	}

	@PutMapping("/switch/{communityId}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Switch active community")
	public ResponseEntity<ApiResponse<UUID>> switchCommunity(
		@Parameter(description = "Community ID to switch to", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable CommunityId communityId) {
		communityService.switchCommunity(communityId.getValue());
		return ResponseEntity.ok(ApiResponse.success("Community switched successfully", communityId.getValue()));
	}

	@GetMapping("/search")
	@PreAuthorize("hasRole('PLATFORM_ADMIN')")
	@Operation(
		summary = "Search communities by name or code",
		description = "Search for communities using community name or community code. The search is case-insensitive and supports partial matching."
	)
	public ResponseEntity<ApiResponse<List<CommunityResponse>>> searchCommunities(
		@Parameter(
			description = "Search term to match against community name or code",
			example = "Greenbelt",
			required = true
		) @RequestParam String searchTerm) {
		List<CommunityResponse> responses = communityService.searchByCommunityNameOrCode(searchTerm);
		return ResponseEntity.ok(ApiResponse.success(responses));
	}
}
