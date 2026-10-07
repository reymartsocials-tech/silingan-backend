package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateCommunityRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;

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

	List<CommunityResponse> searchByCommunityNameOrCode(String searchTerm);
}
