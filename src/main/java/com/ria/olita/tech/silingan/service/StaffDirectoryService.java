package com.ria.olita.tech.silingan.service;

import java.util.List;
import java.util.UUID;

import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public interface StaffDirectoryService {

	List<CommunityStaffMemberResponse> getCommunityStaffDirectory(
		UUID communityId,
		String searchTerm,
		StaffRoleCode role,
		CommunityStaffStatus status
	);
}
