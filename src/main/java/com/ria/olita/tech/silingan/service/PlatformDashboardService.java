package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.PlatformGrowthPeriod;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardResponse;

public interface PlatformDashboardService {

	PlatformDashboardResponse getDashboard(PlatformGrowthPeriod growthPeriod);
}
