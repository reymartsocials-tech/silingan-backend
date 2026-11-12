package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Minimum community information a resident needs to confirm a scanned invitation.
 *
 * <p>Deliberately excludes the community code, invitation token, token hash, usage statistics,
 * administrator details and community configuration: this endpoint is reachable before
 * authentication.
 */
@Builder
@Schema(description = "Community resolved from a scanned join invitation")
public record CommunityJoinInvitationPreviewResponse(
	UUID invitationId,
	UUID communityId,
	String communityName,
	String communityLogoUrl,

	@Schema(description = "When the scanned invitation stops working")
	LocalDateTime expiresAt
) {
}
