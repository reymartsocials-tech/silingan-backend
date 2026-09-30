package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueStatus;

public record ReportResponse(
	UUID id,
	UUID communityId,
	UUID reporterId,
	String reporterName,
	UUID categoryId,
	String categoryName,
	String title,
	String description,
	IssueStatus status,
	String customCategory,
	String imageUrl,
	String location,
	Double latitude,
	Double longitude,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
	public static ReportResponse fromEntity(Issue issue) {
		String reporterName = ((issue.getReporter().getFirstName() == null ? "" : issue.getReporter().getFirstName()) + " "
			+ (issue.getReporter().getLastName() == null ? "" : issue.getReporter().getLastName())).trim();
		if (reporterName.isEmpty()) {
			reporterName = issue.getReporter().getUsername();
		}
		UUID communityId = issue.getCommunityId() != null
			? issue.getCommunityId()
			: (issue.getCommunity() != null ? issue.getCommunity().getId() : null);
		return new ReportResponse(
			issue.getId(),
			communityId,
			issue.getReporter().getId(),
			reporterName,
			issue.getCategory().getId(),
			issue.getCategory().getName(),
			issue.getTitle(),
			issue.getDescription(),
			issue.getStatus(),
			issue.getCustomCategory(),
			issue.getImageUrl(),
			issue.getLocation(),
			issue.getLatitude(),
			issue.getLongitude(),
			issue.getCreatedAt(),
			issue.getUpdatedAt()
		);
	}
}
