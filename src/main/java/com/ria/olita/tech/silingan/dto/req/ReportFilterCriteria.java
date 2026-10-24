package com.ria.olita.tech.silingan.dto.req;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.UrgencyLevel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for reports")
public class ReportFilterCriteria {

	@Schema(description = "Filter by report status", example = "OPEN")
	private IssueStatus status;

	@Schema(description = "Filter by urgency level", example = "HIGH")
	private UrgencyLevel urgencyLevel;

	@Schema(description = "Filter by issue category ID")
	private UUID categoryId;

	@Schema(description = "Filter by assigned-to user ID")
	private UUID assignedToId;

	@Schema(description = "Filter by reporter user ID")
	private UUID reporterId;

	@Schema(description = "Filter by assigned status (true = assigned, false = unassigned)", example = "true")
	private Boolean assigned;

	@Schema(description = "Search keyword in title or description")
	private String keyword;

	@Schema(description = "Filter by location")
	private String location;

	@Schema(description = "Filter reports created from this date")
	private LocalDateTime createdAtFrom;

	@Schema(description = "Filter reports created until this date")
	private LocalDateTime createdAtTo;

	@Schema(description = "Filter reports resolved from this date")
	private LocalDateTime resolvedAtFrom;

	@Schema(description = "Filter reports resolved until this date")
	private LocalDateTime resolvedAtTo;
}
