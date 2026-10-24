package com.ria.olita.tech.silingan.rest;

import com.ria.olita.tech.silingan.dto.res.AnnouncementInteractionResponse;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentAnnouncementResponse;
import com.ria.olita.tech.silingan.service.AnnouncementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Resident-facing, read-only view over the announcement module. The community is always derived
 * from the authenticated resident's active membership, so no community identifier is accepted
 * from the client.
 */
@RestController
@RequestMapping("/api/v1/secure/announcements")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated() && hasRole('RESIDENT')")
@Tag(name = "Resident Announcement", description = "Published announcement APIs for residents")
public class SecuredAnnouncementController {

	private final AnnouncementService announcementService;

	@GetMapping
	@Operation(summary = "Get published announcements of the authenticated resident's active community")
	public ResponseEntity<ApiResponse<Page<ResidentAnnouncementResponse>>> getPublishedAnnouncements(Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(
			announcementService.getPublishedAnnouncementsForResident(pageable)));
	}

	@GetMapping("/{announcementId}")
	@Operation(summary = "Get a published announcement of the authenticated resident's active community")
	public ResponseEntity<ApiResponse<ResidentAnnouncementResponse>> getPublishedAnnouncement(
		@Parameter(description = "Announcement ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID announcementId
	) {
		return ResponseEntity.ok(ApiResponse.success(
			announcementService.getPublishedAnnouncementForResident(announcementId)));
	}

	@PostMapping("/{announcementId}/acknowledgements")
	@Operation(summary = "Acknowledge that the authenticated resident has read the announcement")
	public ResponseEntity<ApiResponse<AnnouncementInteractionResponse>> acknowledge(
		@Parameter(description = "Announcement ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID announcementId
	) {
		return ResponseEntity.ok(ApiResponse.success(
			announcementService.acknowledgeAnnouncement(announcementId)));
	}

	@PostMapping("/{announcementId}/helpful")
	@Operation(summary = "Mark the announcement as helpful for the authenticated resident")
	public ResponseEntity<ApiResponse<AnnouncementInteractionResponse>> markHelpful(
		@Parameter(description = "Announcement ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID announcementId
	) {
		return ResponseEntity.ok(ApiResponse.success(
			announcementService.markAnnouncementHelpful(announcementId)));
	}
}
