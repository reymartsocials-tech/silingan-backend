package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;

public record IssueRoutingResponse(
	UUID categoryId,
	String categoryName,
	boolean categoryActive,
	IssueRoutingDestination destinationType,
	RoutingStaffSummaryResponse assignedStaff,
	IssueRoutingStatus routingStatus,
	LocalDateTime updatedAt
) {
}
