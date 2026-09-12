package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateCommunityRequest;
import com.ria.olita.tech.silingan.dto.res.AssignCommunityAdministratorResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityAdminInvitationStatusResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.entity.CommunityAdminInvitationStatus;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CommunityService {

	CommunityResponse create(CreateCommunityRequest request);

	CommunityResponse getById(UUID id);

	CommunityResponse getByCode(String code);

	List<CommunityResponse> getAll();

	List<CommunityResponse> getByUserId(UUID userId);

	List<CommunityResponse> getByStatus(CommunityStatus status);

	List<CommunityResponse> getByType(CommunityType type);

	CommunityResponse update(UUID id, UpdateCommunityRequest request);

	void updateStatus(UUID id, CommunityStatus status);

	void delete(UUID id);

	void switchCommunity(UUID communityId);

	AssignCommunityAdministratorResponse assignAdministrator(UUID communityId, String email);

	Page<CommunityAdminInvitationStatusResponse> getAdministratorInvitations(
		UUID communityId,
		CommunityAdminInvitationStatus status,
		Pageable pageable
	);

	List<CommunityResponse> searchByCommunityNameOrCode(String searchTerm);
}
