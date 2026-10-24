package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.UrgencyLevel;

public record UpdateReportRequest(
	String title,
	String description,
	UUID categoryId,
	String customCategory,
	UrgencyLevel urgencyLevel,
	UUID assignedToUserId,
	String location,
	Double latitude,
	Double longitude,
	IssueStatus status,
	String resolutionNotes
) {
}
