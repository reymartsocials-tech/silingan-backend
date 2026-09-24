package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record StaffInvitationResponse(
	UUID invitationId,
	UUID communityId,
	String firstName,
	String lastName,
	String email,
	String mobileNumber,
	String position,
	StaffRoleCode roleCode,
	StaffInvitationStatus status,
	LocalDateTime invitedAt,
	LocalDateTime expiresAt,
	LocalDateTime acceptedAt,
	LocalDateTime revokedAt,
	String invitedByName,
	String notes
) {
}
