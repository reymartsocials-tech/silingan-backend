package com.ria.olita.tech.silingan.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.CommunityJoinInvitationPreviewRequest;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationPreviewResponse;
import com.ria.olita.tech.silingan.security.scope.SkipCommunityScopeCheck;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Pre-authentication resolution of a scanned join invitation.
 *
 * <p>The token is sent in the request body, not the query string, so it is not retained by browser
 * history, proxies, gateways or access logs.
 */
@RestController
@RequestMapping("/api/v1/public/community-join-invitations")
@RequiredArgsConstructor
@Tag(name = "Public Community Join Invitation", description = "Resolve a scanned join QR before authentication")
@SkipCommunityScopeCheck(reason = "The community is resolved from the invitation token, not from caller scope")
public class PublicCommunityJoinInvitationController {

	private final CommunityJoinInvitationService communityJoinInvitationService;

	@PermitAll
	@PostMapping("/preview")
	@Operation(
		summary = "Preview the community behind a scanned join invitation",
		description = """
			Resolves a scanned invitation token to the minimum community information a resident needs
			in order to confirm before onboarding. Previewing does not authenticate the resident, does
			not consume a use of the invitation and does not create a membership or request. The
			response is not authoritative: the invitation is validated again during registration.
			"""
	)
	public ResponseEntity<ApiResponse<CommunityJoinInvitationPreviewResponse>> preview(
		@Valid @RequestBody CommunityJoinInvitationPreviewRequest request
	) {
		CommunityJoinInvitationPreviewResponse response = communityJoinInvitationService.preview(request.token());
		return ResponseEntity.ok(ApiResponse.success("Invitation is valid", response));
	}
}
