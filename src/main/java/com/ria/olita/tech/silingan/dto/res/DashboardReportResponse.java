package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.UrgencyLevel;

/** Deliberately omits the description, reporter and location. */
public record DashboardReportResponse(
	UUID id,
	String title,
	String category,
	IssueStatus status,
	UrgencyLevel urgencyLevel,
	LocalDateTime submittedAt
) {
}
