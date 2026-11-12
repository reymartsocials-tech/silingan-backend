package com.ria.olita.tech.silingan.rest;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrImage;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrMetadataResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.qr.CommunityJoinQrService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Community Admin APIs for the resident join QR code.
 *
 * <p>The QR only encodes the community's existing join code inside the configured SILINGAN join
 * URL. Scanning it does not authenticate the resident and does not create a membership: the
 * resident still completes OTP authentication, the existing community-code validation
 * ({@code GET /api/v1/public/communities/code/{code}}), explicit confirmation, and the existing
 * personal-information onboarding.
 */
@RestController
@RequestMapping("/api/v1/communities")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Community Join QR", description = "Community join QR code APIs for Community Admins")
public class CommunityJoinQrController {

	private static final Duration CACHE_TTL = Duration.ofMinutes(5);

	private final CommunityJoinQrService communityJoinQrService;

	@GetMapping(value = "/{communityId}/join-qr", produces = MediaType.IMAGE_PNG_VALUE)
	@RequiresPermission(domain = Domain.COMMUNITY, action = Action.VIEW)
	@Operation(
		summary = "Get the community join QR code",
		description = """
			Returns a PNG QR code encoding the SILINGAN join URL for the community's existing
			community code. Requires an authenticated caller with COMMUNITY:VIEW permission for the
			community; cross-community access is rejected. The image is rendered per request and is
			never stored.
			"""
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "QR code image"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller may not access this community", content = @io.swagger.v3.oas.annotations.media.Content),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community not found", content = @io.swagger.v3.oas.annotations.media.Content),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Community is not accepting new residents", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public ResponseEntity<byte[]> getJoinQr(
		@Parameter(description = "Community ID", example = "5ee38760-cefd-4f0a-a8d2-4941c77fc792")
		@PathVariable UUID communityId) {
		// communityId is re-authorized against the caller's memberships in the service layer.
		CommunityJoinQrImage qr = communityJoinQrService.generateJoinQr(communityId);

		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(CACHE_TTL)
				.cachePrivate())
			.header(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.inline()
					.filename(qr.fileName())
					.build()
					.toString()
			)
			.body(qr.content());
	}

	@GetMapping("/{communityId}/join-qr/metadata")
	@RequiresPermission(domain = Domain.COMMUNITY, action = Action.VIEW)
	@Operation(
		summary = "Get community join QR metadata",
		description = "Returns the community display information, join code and join URL encoded in the join QR code."
	)
	public ResponseEntity<ApiResponse<CommunityJoinQrMetadataResponse>> getJoinQrMetadata(
		@Parameter(description = "Community ID", example = "5ee38760-cefd-4f0a-a8d2-4941c77fc792")
		@PathVariable UUID communityId) {
		CommunityJoinQrMetadataResponse response = communityJoinQrService.getJoinQrMetadata(communityId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}
}
