package com.ria.olita.tech.silingan.service.invitation;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationCreatedResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationPreviewResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationResponse;

/**
 * Lifecycle of the revocable, expiring invitations that back community join QR codes and links.
 *
 * <p>An invitation only selects a community. It never authenticates a resident and never replaces
 * OTP verification or the existing membership rules.
 */
public interface CommunityJoinInvitationService {

	/**
	 * Creates an invitation and returns the only copy of the raw join URL and QR image.
	 */
	CommunityJoinInvitationCreatedResponse create(UUID communityId, CreateCommunityJoinInvitationRequest request);

	/**
	 * Lists a community's invitations, newest first. Metadata only.
	 */
	Page<CommunityJoinInvitationResponse> list(UUID communityId, Pageable pageable);

	/**
	 * Revokes an invitation so no further preview or registration can use it. Idempotent.
	 */
	CommunityJoinInvitationResponse revoke(UUID communityId, UUID invitationId);

	/**
	 * Resolves a scanned token to the minimum community information a resident needs in order to
	 * confirm. Never consumes a use.
	 */
	CommunityJoinInvitationPreviewResponse preview(String rawToken);

	/**
	 * Re-validates a token at registration time and resolves it through the same community
	 * eligibility rules used by manual community-code entry.
	 */
	CommunityJoinInvitationResolution resolveForRegistration(String rawToken);

	/**
	 * Atomically records one successful registration against the invitation.
	 */
	void consumeUse(UUID invitationId);
}
