package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

import lombok.Builder;

@Builder
public record InvitationSummaryResponse(
	UUID invitationId,
	String email,
	String invitedName,
	StaffRoleCode roleCode,
	String status,
	LocalDateTime invitedAt,
	LocalDateTime expiresAt,
	LocalDateTime acceptedAt,
	String invitedByName,
	UUID communityId
) {
}
