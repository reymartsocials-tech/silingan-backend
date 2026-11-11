package com.ria.olita.tech.silingan.dto.res;

import com.ria.olita.tech.silingan.entity.IssueStatus;

public record DashboardReportStatusResponse(
	IssueStatus status,
	long count
) {
}
