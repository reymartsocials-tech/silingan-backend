package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationStatus;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationType;
import com.ria.olita.tech.silingan.entity.InvitationDistributionChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Join invitation metadata. Never carries the raw token, the token hash or the join URL.
 */
@Builder
@Schema(description = "Community join invitation metadata")
public record CommunityJoinInvitationResponse(
	UUID invitationId,
	UUID communityId,
	String communityName,
	CommunityJoinInvitationType type,
	String label,
	InvitationDistributionChannel distributionChannel,

	@Schema(description = "Status evaluated against the current time and usage, not just the stored value")
	CommunityJoinInvitationStatus status,

	String createdByName,
	LocalDateTime createdAt,
	LocalDateTime expiresAt,
	Integer maxUses,
	Integer usedCount,
	LocalDateTime lastUsedAt,
	String revokedByName,
	LocalDateTime revokedAt
) {
}
