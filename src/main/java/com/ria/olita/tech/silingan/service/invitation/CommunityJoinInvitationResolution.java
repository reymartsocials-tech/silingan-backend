package com.ria.olita.tech.silingan.service.invitation;

import java.util.UUID;

import com.ria.olita.tech.silingan.dto.res.CommunityResponse;

/**
 * Community resolved from an invitation token, together with the invitation whose usage must be
 * recorded once registration succeeds.
 */
public record CommunityJoinInvitationResolution(
	UUID invitationId,
	CommunityResponse community
) {
}
