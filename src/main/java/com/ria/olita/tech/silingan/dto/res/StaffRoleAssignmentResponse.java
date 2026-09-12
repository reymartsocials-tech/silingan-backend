package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record StaffRoleAssignmentResponse(
	UUID userId,
	UUID communityId,
	StaffRoleCode roleCode,
	Boolean active,
	LocalDateTime assignedAt,
	UUID assignedBy
) {
}
