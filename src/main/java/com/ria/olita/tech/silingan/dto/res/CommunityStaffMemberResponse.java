package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record CommunityStaffMemberResponse(
	UUID userId,
	String fullName,
	StaffRoleCode role,
	String email,
	String mobileNumber,
	CommunityStaffStatus status
) {
}
