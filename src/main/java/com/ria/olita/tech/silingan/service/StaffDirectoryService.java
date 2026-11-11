package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.dto.res.StaffDetailsResponse;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public interface StaffDirectoryService {

	Page<CommunityStaffMemberResponse> getCommunityStaffDirectory(
		UUID communityId,
		String searchTerm,
		StaffRoleCode role,
		CommunityStaffStatus status,
		Pageable pageable
	);

	StaffDetailsResponse getStaffMember(UUID communityId, UUID staffId);
}
