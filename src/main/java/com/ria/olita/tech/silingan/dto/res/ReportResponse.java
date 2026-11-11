package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.UrgencyLevel;

public record ReportResponse(
	UUID id,
	UUID communityId,
	UUID reporterId,
	String reporterName,
	UUID assignedToId,
	String assignedToName,
	UUID categoryId,
	String categoryName,
	String title,
	String description,
	IssueStatus status,
	UrgencyLevel urgencyLevel,
	String customCategory,
	String location,
	Double latitude,
	Double longitude,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	LocalDateTime resolvedAt,
	String resolutionNotes,
	List<AttachmentResponse> attachments
) {
	public static ReportResponse fromEntity(Issue issue) {
		String reporterName = ((issue.getReporter().getFirstName() == null ? "" : issue.getReporter().getFirstName()) + " "
			+ (issue.getReporter().getLastName() == null ? "" : issue.getReporter().getLastName())).trim();
		if (reporterName.isEmpty()) {
			reporterName = issue.getReporter().getUsername();
		}
		
		String assignedToName = null;
		UUID assignedToId = null;
		if (issue.getAssignedTo() != null) {
			assignedToName = ((issue.getAssignedTo().getFirstName() == null ? "" : issue.getAssignedTo().getFirstName()) + " "
				+ (issue.getAssignedTo().getLastName() == null ? "" : issue.getAssignedTo().getLastName())).trim();
			if (assignedToName.isEmpty()) {
				assignedToName = issue.getAssignedTo().getUsername();
			}
			assignedToId = issue.getAssignedTo().getId();
		}
		
		UUID communityId = issue.getCommunityId() != null
			? issue.getCommunityId()
			: (issue.getCommunity() != null ? issue.getCommunity().getId() : null);
		return new ReportResponse(
			issue.getId(),
			communityId,
			issue.getReporter().getId(),
			reporterName,
			assignedToId,
			assignedToName,
			issue.getCategory().getId(),
			issue.getCategory().getName(),
			issue.getTitle(),
			issue.getDescription(),
			issue.getStatus(),
			issue.getUrgencyLevel(),
			issue.getCustomCategory(),
			issue.getLocation(),
			issue.getLatitude(),
			issue.getLongitude(),
			issue.getCreatedAt(),
			issue.getUpdatedAt(),
			issue.getResolvedAt(),
			issue.getResolutionNotes(),
			null
		);
	}
}
