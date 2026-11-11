package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import com.ria.olita.tech.silingan.dto.res.CommunityDashboardResponse;

public interface CommunityDashboardService {

	CommunityDashboardResponse getDashboard(UUID communityId);
}
