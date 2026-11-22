package com.ria.olita.tech.silingan.service;

import java.util.List;
import java.util.UUID;

import com.ria.olita.tech.silingan.dto.req.UpsertIssueRoutingRequest;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingResponse;
import com.ria.olita.tech.silingan.dto.res.RoutingStaffSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoutingDependenciesResponse;

public interface IssueRoutingService {

	List<IssueRoutingResponse> getRoutingConfiguration(UUID communityId);

	IssueRoutingResponse upsertRouting(UUID communityId, UUID categoryId, UpsertIssueRoutingRequest request);

	List<RoutingStaffSummaryResponse> getEligibleStaff(UUID communityId, UUID categoryId);

	StaffRoutingDependenciesResponse getStaffRoutingDependencies(UUID communityId, UUID staffUserId);
}
